<#
.SYNOPSIS
  MiniAIalipay 本地一键构建并上传部署产物到服务器。

.DESCRIPTION
  执行步骤：
  1. 清理并重建暂存目录 deploy/production/stage；
  2. mvn -DskipTests package 构建 5 个后端 fat jar，收集到 stage/jars/；
  3. 分别构建 frontend-h5 与 frontend-admin，产物收集到 stage/web/h5、stage/web/admin；
  4. 连同 docker-compose.yml、Dockerfile.backend、nginx 配置、.env.example 一并 scp 上传。

  服务器上首次部署还需：cp .env.example .env 并填写密钥后 docker compose up -d。
  详细步骤见同目录 README.md。

.PARAMETER Server
  目标服务器地址，默认 114.55.75.194。
.PARAMETER User
  SSH 登录用户，默认 root（需已配置密钥或可交互输入密码）。
.PARAMETER RemoteDir
  服务器部署目录，默认 /opt/minialalipay。
.PARAMETER SkipBackend
  跳过后端构建（jar 未变化时复用上次产物）。
.PARAMETER SkipFrontend
  跳过前端构建。
.PARAMETER SkipUpload
  只构建不上传，用于本地验证构建产物。

.EXAMPLE
  .\build-and-upload.ps1
  .\build-and-upload.ps1 -SkipFrontend
  .\build-and-upload.ps1 -SkipUpload
#>
param(
    [string]$Server = "114.55.75.194",
    [string]$User = "root",
    [string]$RemoteDir = "/opt/minialalipay",
    [switch]$SkipBackend,
    [switch]$SkipFrontend,
    [switch]$SkipUpload
)

$ErrorActionPreference = "Stop"

# 仓库根目录与暂存目录
$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")
$stage = Join-Path $PSScriptRoot "stage"

# 后端 5 个可部署服务（platform-common 为公共库，不独立部署）
$services = @("gateway", "user-center", "business-center", "account-center", "ai-service")

# 暂存目录不全量清空：跳过前端/后端构建时需要复用上次产物，
# 否则会把空目录上传到服务器，导致 Nginx 找不到 index.html 返回 403
Write-Host "==> 准备暂存目录 $stage（保留已有产物）" -ForegroundColor Cyan
New-Item -ItemType Directory -Path (Join-Path $stage "jars") -Force | Out-Null
New-Item -ItemType Directory -Path (Join-Path $stage "web\h5") -Force | Out-Null
New-Item -ItemType Directory -Path (Join-Path $stage "web\admin") -Force | Out-Null

# ---------- 1. 收集部署配置文件 ----------
Write-Host "==> 复制部署配置（compose / Dockerfile / nginx / .env 模板）" -ForegroundColor Cyan
Copy-Item (Join-Path $PSScriptRoot "docker-compose.yml") $stage
Copy-Item (Join-Path $PSScriptRoot "Dockerfile.backend") $stage
Copy-Item (Join-Path $PSScriptRoot ".env.example") $stage
# HTTPS 一键启用脚本（服务器侧生成自签证书、重写 CORS、重建 nginx/gateway）
Copy-Item (Join-Path $PSScriptRoot "enable-https.sh") $stage
# nginx 配置目录：先清空再整体复制，避免重复运行时 PowerShell 把源目录嵌套进已有目标（stage\nginx\nginx）；
# 证书只在服务器生成，本地 nginx 目录不含 certs，清空无风险
$nginxStage = Join-Path $stage "nginx"
Remove-Item $nginxStage -Recurse -Force -ErrorAction SilentlyContinue
Copy-Item (Join-Path $PSScriptRoot "nginx") $nginxStage -Recurse

# ---------- 2. 后端构建 ----------
if (-not $SkipBackend) {
    Write-Host "==> 构建后端（mvn -DskipTests package，约需数分钟）" -ForegroundColor Cyan
    Push-Location (Join-Path $repoRoot "backend")
    try {
        mvn -DskipTests package -q
        if ($LASTEXITCODE -ne 0) { throw "后端 Maven 构建失败（退出码 $LASTEXITCODE）" }
    } finally {
        Pop-Location
    }
}

# 收集 fat jar：Spring Boot repackage 同时产生 *.jar.original 瘦包，必须排除
foreach ($svc in $services) {
    $jar = Get-ChildItem (Join-Path $repoRoot "backend\$svc\target\*.jar") -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -notlike "*.original" } |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
    if (-not $jar) { throw "未找到 $svc 的构建产物，请确认后端构建成功（可去掉 -SkipBackend 重跑）" }
    Copy-Item $jar.FullName (Join-Path $stage "jars\$svc.jar")
    Write-Host "    收集 $svc.jar ($([Math]::Round($jar.Length / 1MB, 1)) MB)"
}

# ---------- 3. 前端构建 ----------
if (-not $SkipFrontend) {
    foreach ($proj in @(@{ Name = "frontend-h5"; Target = "h5" }, @{ Name = "frontend-admin"; Target = "admin" })) {
        Write-Host "==> 构建前端 $($proj.Name)（npm run build）" -ForegroundColor Cyan
        $projDir = Join-Path $repoRoot $proj.Name
        Push-Location $projDir
        try {
            npm run build
            if ($LASTEXITCODE -ne 0) { throw "$($proj.Name) 构建失败（退出码 $LASTEXITCODE）" }
        } finally {
            Pop-Location
        }
        $dist = Join-Path $projDir "dist"
        if (-not (Test-Path $dist)) { throw "$($proj.Name) 未产出 dist 目录" }
        # 重建前先清空对应产物目录，避免残留旧版文件
        $targetDir = Join-Path $stage "web\$($proj.Target)"
        Remove-Item $targetDir -Recurse -Force -ErrorAction SilentlyContinue
        New-Item -ItemType Directory -Path $targetDir -Force | Out-Null
        Copy-Item "$dist\*" $targetDir -Recurse -Force
    }
} else {
    # 跳过前端构建时复用上次产物；若产物不存在必须全量构建，禁止上传空目录
    foreach ($t in @("h5", "admin")) {
        if (-not (Test-Path (Join-Path $stage "web\$t\index.html"))) {
            throw "stage\web\$t 缺少 index.html，不能使用 -SkipFrontend 上传；请去掉 -SkipFrontend 重新运行"
        }
    }
    Write-Host "==> 跳过前端构建，复用 stage/web 中的上次产物" -ForegroundColor Yellow
}

# ---------- 4. 上传 ----------
if ($SkipUpload) {
    Write-Host "==> 已跳过上传；产物位于 $stage" -ForegroundColor Green
    exit 0
}

$remote = "${User}@${Server}"
Write-Host "==> 上传到 ${remote}:${RemoteDir}" -ForegroundColor Cyan
ssh $remote "mkdir -p $RemoteDir"
if ($LASTEXITCODE -ne 0) { throw "SSH 连接失败，请确认网络与密钥配置" }

# 逐项上传，避免通配符在不同 shell 下行为差异
Get-ChildItem $stage | ForEach-Object {
    Write-Host "    上传 $($_.Name) ..."
    scp -r $_.FullName "${remote}:${RemoteDir}/"
    if ($LASTEXITCODE -ne 0) { throw "上传 $($_.Name) 失败" }
}

Write-Host @"

==> 上传完成。接下来在服务器上执行：
    cd $RemoteDir
    # 首次部署：填写密钥（内部令牌、数据库密码等）
    cp .env.example .env && vi .env
    # 启动（Nacos 中无 seata-server 时追加 --profile seata）
    docker compose up -d --build
    docker compose ps
"@ -ForegroundColor Green
