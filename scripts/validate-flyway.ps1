# =====================================================================
# Flyway 迁移预检脚本
#
# 用途：合并分支、切换分支或修改迁移文件后，在启动服务之前统一校验
#       各服务数据库的 flyway_schema_history 与本地迁移文件是否一致，
#       提前发现校验和不匹配（该问题会导致服务启动时以 sqlSessionTemplate
#       等表层错误失败，真实根因藏在日志深处）。
#
# 用法（在 scripts 目录或任意目录用全路径执行）：
#   .\validate-flyway.ps1                     # 校验全部四个服务
#   .\validate-flyway.ps1 -Service user-center # 只校验指定服务
#   .\validate-flyway.ps1 -Repair             # 校验并对不一致项执行 repair
#                                             #（仅对齐 checksum，不重放业务 SQL）
#
# 依赖：JDK 21（优先 JAVA_HOME）、本地 Maven 仓库中的
#       flyway-core / flyway-mysql / gson / slf4j-api / mysql-connector-j / jackson。
# =====================================================================
param(
    [string]$Service = '',
    [switch]$Repair,
    [string]$MvnRepo = 'D:\develop\apache-maven-3.9.4\mvn_repo',
    [string]$DbHost = '121.43.51.164',
    [string]$DbUser = 'root',
    [string]$DbPassword = 'teamuser2026'
)

$ErrorActionPreference = 'Stop'
# 控制台按 UTF-8 输出，避免中文日志乱码（JDK 21 默认即以 UTF-8 输出）
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$scriptsDir = $PSScriptRoot
$repoRoot = Split-Path -Parent $scriptsDir

# 定位 java 可执行文件：优先 JAVA_HOME，其次 PATH
$java = 'java'
if ($env:JAVA_HOME) {
    $java = Join-Path $env:JAVA_HOME 'bin\java.exe'
}
if (-not (Test-Path $java -ErrorAction SilentlyContinue)) {
    $java = 'java'
}

# 与各服务 application.yml 保持一致的 Flyway 依赖（版本来自本地 Maven 仓库）
$jars = @(
    "$MvnRepo\org\flywaydb\flyway-core\10.10.0\flyway-core-10.10.0.jar",
    "$MvnRepo\org\flywaydb\flyway-mysql\10.10.0\flyway-mysql-10.10.0.jar",
    "$MvnRepo\com\google\code\gson\gson\2.10.1\gson-2.10.1.jar",
    "$MvnRepo\org\slf4j\slf4j-api\2.0.13\slf4j-api-2.0.13.jar",
    "$MvnRepo\com\mysql\mysql-connector-j\8.3.0\mysql-connector-j-8.3.0.jar",
    "$MvnRepo\com\fasterxml\jackson\core\jackson-databind\2.17.2\jackson-databind-2.17.2.jar",
    "$MvnRepo\com\fasterxml\jackson\core\jackson-core\2.17.2\jackson-core-2.17.2.jar",
    "$MvnRepo\com\fasterxml\jackson\core\jackson-annotations\2.17.2\jackson-annotations-2.17.2.jar"
)
foreach ($jar in $jars) {
    if (-not (Test-Path $jar)) {
        Write-Error "缺少依赖 jar：$jar （可用 -MvnRepo 指定其他本地仓库路径）"
    }
}
$classpath = ($jars -join ';')

# 四个持有数据库迁移的服务；OutOfOrder 与各服务 application.yml 的
# spring.flyway.out-of-order 配置保持一致（目前仅 business-center 开启）
$services = @(
    @{ Name = 'user-center';     Schema = 'user_db';     Dir = 'backend\user-center\src\main\resources\db\migration';     OutOfOrder = $false },
    @{ Name = 'account-center';  Schema = 'account_db';  Dir = 'backend\account-center\src\main\resources\db\migration';  OutOfOrder = $false },
    @{ Name = 'business-center'; Schema = 'business_db'; Dir = 'backend\business-center\src\main\resources\db\migration'; OutOfOrder = $true  },
    @{ Name = 'ai-service';      Schema = 'agent_db';    Dir = 'backend\ai-service\src\main\resources\db\migration';      OutOfOrder = $false }
)

if ($Service) {
    $services = @($services | Where-Object { $_.Name -eq $Service })
    if ($services.Count -eq 0) {
        Write-Error "未知服务：$Service （可选：user-center / account-center / business-center / ai-service）"
    }
}

$validator = Join-Path $scriptsDir 'FlywayValidate.java'
$failed = @()

foreach ($svc in $services) {
    Write-Host ""
    Write-Host "===== $($svc.Name) / $($svc.Schema) =====" -ForegroundColor Cyan
    $jdbcUrl = "jdbc:mysql://${DbHost}:3306/$($svc.Schema)?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&characterEncoding=UTF-8"
    $migDir = Join-Path $repoRoot $svc.Dir
    $toolArgs = @('-cp', "$classpath;$scriptsDir", $validator, $jdbcUrl, $DbUser, $DbPassword, $svc.Schema, $migDir, "$($svc.OutOfOrder)".ToLowerInvariant())
    if ($Repair) { $toolArgs += '--repair' }

    & $java @toolArgs
    if ($LASTEXITCODE -ne 0) { $failed += $svc.Name }
}

Write-Host ""
Write-Host "===== 预检汇总 =====" -ForegroundColor Cyan
if ($failed.Count -eq 0) {
    Write-Host "全部服务 Flyway 校验通过，可以安全启动服务。" -ForegroundColor Green
    exit 0
} else {
    Write-Host "以下服务校验失败：$($failed -join ', ')" -ForegroundColor Red
    Write-Host "处理建议：确认本地迁移文件是否为预期版本；若文件内容即为正确版本，执行 .\validate-flyway.ps1 -Repair 对齐校验和。" -ForegroundColor Yellow
    exit 1
}
