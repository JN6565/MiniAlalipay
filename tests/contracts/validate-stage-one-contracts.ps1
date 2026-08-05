param(
    [string]$RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
)

$ErrorActionPreference = 'Stop'

function Assert-Contract {
    param(
        [bool]$Condition,
        [string]$Message
    )
    if (-not $Condition) {
        throw "阶段一契约校验失败：$Message"
    }
}

$catalogPath = Join-Path $RepositoryRoot 'contracts/openapi/p0-interface-catalog.yaml'
$openApiPath = Join-Path $RepositoryRoot 'contracts/openapi/minialalipay-api.yaml'
$errorCodePath = Join-Path $RepositoryRoot 'contracts/error-codes/error-codes.yaml'
$eventTypePath = Join-Path $RepositoryRoot 'contracts/events/event-types.yaml'
$eventEnvelopePath = Join-Path $RepositoryRoot 'contracts/events/event-envelope.schema.json'
$requestIdJavaPath = Join-Path $RepositoryRoot 'backend/platform-common/src/main/java/com/minialalipay/common/trace/RequestIdGenerator.java'
$idempotencyJavaPath = Join-Path $RepositoryRoot 'backend/platform-common/src/main/java/com/minialalipay/common/idempotency/IdempotencyKeyValidator.java'
$commonErrorJavaPath = Join-Path $RepositoryRoot 'backend/platform-common/src/main/java/com/minialalipay/common/error/CommonErrorCode.java'

$catalog = Get-Content -Encoding utf8 $catalogPath
$policyNames = $catalog |
    Where-Object { $_ -match '^  ([A-Z_]+): \{headerRequired:' } |
    ForEach-Object { [regex]::Match($_, '^  ([A-Z_]+):').Groups[1].Value }
$operationLines = $catalog | Where-Object { $_ -match '^  - \{method:' }
$operations = foreach ($line in $operationLines) {
    $match = [regex]::Match(
        $line,
        '^  - \{method: ([A-Z]+), path: (.+?), operationId: ([^,]+), owner: ([^,]+), clientScope: ([^,]+), idempotency: ([A-Z_]+)\}$'
    )
    Assert-Contract $match.Success "无法解析 P0 操作：$line"
    [pscustomobject]@{
        Method = $match.Groups[1].Value
        Path = $match.Groups[2].Value.Trim("'")
        OperationId = $match.Groups[3].Value
        Owner = $match.Groups[4].Value
        ClientScope = $match.Groups[5].Value
        Idempotency = $match.Groups[6].Value
    }
}

Assert-Contract ($operations.Count -eq 80) "P0 操作数量应为 80，实际为 $($operations.Count)"
Assert-Contract (($operations | Group-Object Method, Path | Where-Object Count -gt 1).Count -eq 0) '方法与路径存在重复'
Assert-Contract (($operations | Group-Object OperationId | Where-Object Count -gt 1).Count -eq 0) 'operationId 存在重复'
Assert-Contract (($operations | Where-Object Owner -notin @('gateway', 'user-center', 'business-center', 'account-center', 'ai-service')).Count -eq 0) '存在未知服务所有者'
Assert-Contract (($operations | Where-Object ClientScope -notin @('B', 'C', 'SHARED', 'INTERNAL')).Count -eq 0) '存在未知调用端'
Assert-Contract (($operations | Where-Object Idempotency -notin $policyNames).Count -eq 0) '存在未定义的幂等策略'
Assert-Contract (($operations | Where-Object Idempotency -eq 'NONE').OperationId -join ',' -eq 'login') 'NONE 只能用于登录操作'

$errorLines = Get-Content -Encoding utf8 $errorCodePath | Where-Object { $_ -match '^  [A-Z0-9_]+: \{httpStatus:' }
$errors = foreach ($line in $errorLines) {
    $match = [regex]::Match($line, '^  ([A-Z0-9_]+): \{httpStatus: ([0-9]+), message: (.+)\}$')
    Assert-Contract $match.Success "无法解析错误码：$line"
    [pscustomobject]@{ Code = $match.Groups[1].Value; Signature = "$($match.Groups[2].Value)|$($match.Groups[3].Value)" }
}
Assert-Contract (($errors | Group-Object Code | Where-Object Count -gt 1).Count -eq 0) '错误码名称重复'
Assert-Contract (($errors | Group-Object Signature | Where-Object Count -gt 1).Count -eq 0) '存在 HTTP 状态与中文含义完全相同的错误码别名'

$commonErrorLines = Get-Content -Encoding utf8 $commonErrorJavaPath | Where-Object {
    $_ -match '^\s+[A-Z_]+\("(?:OK|COMMON_[A-Z_]+)",'
}
$commonEnumErrors = foreach ($line in $commonErrorLines) {
    $match = [regex]::Match($line, '^\s+[A-Z_]+\("(OK|COMMON_[A-Z_]+)", "(.+)", ([0-9]+)\)[,;]$')
    Assert-Contract $match.Success "无法解析公共错误码枚举：$line"
    [pscustomobject]@{
        Code = $match.Groups[1].Value
        Signature = "$($match.Groups[3].Value)|$($match.Groups[2].Value)"
    }
}
$commonContractErrors = $errors | Where-Object { $_.Code -eq 'OK' -or $_.Code.StartsWith('COMMON_') }
Assert-Contract ($commonEnumErrors.Count -eq $commonContractErrors.Count) '公共错误码枚举与 YAML 数量不一致'
foreach ($contractError in $commonContractErrors) {
    $enumError = $commonEnumErrors | Where-Object Code -eq $contractError.Code
    Assert-Contract ($null -ne $enumError) "公共错误码枚举缺少 $($contractError.Code)"
    Assert-Contract ($enumError.Signature -eq $contractError.Signature) "公共错误码 $($contractError.Code) 的 message 或 httpStatus 与 YAML 不一致"
}

$eventLines = Get-Content -Encoding utf8 $eventTypePath | Where-Object { $_ -match '^  [A-Za-z0-9_.]+: \{version:' }
$eventNames = $eventLines | ForEach-Object { [regex]::Match($_, '^  ([A-Za-z0-9_.]+):').Groups[1].Value }
Assert-Contract ($eventNames.Count -eq 30) "事件类型数量应为 30，实际为 $($eventNames.Count)"
Assert-Contract (($eventNames | Group-Object | Where-Object Count -gt 1).Count -eq 0) '事件类型重复'

$envelope = Get-Content -Encoding utf8 -Raw $eventEnvelopePath | ConvertFrom-Json
foreach ($requiredField in @('eventId', 'eventType', 'eventVersion', 'occurredAt', 'producer', 'traceId', 'payload')) {
    Assert-Contract ($requiredField -in $envelope.required) "事件 Envelope 缺少必填字段 $requiredField"
}

$openApi = Get-Content -Encoding utf8 -Raw $openApiPath
$requestIdJava = Get-Content -Encoding utf8 -Raw $requestIdJavaPath
$idempotencyJava = Get-Content -Encoding utf8 -Raw $idempotencyJavaPath
Assert-Contract ($openApi.Contains("pattern: '^[A-Za-z0-9._:-]+$'")) 'OpenAPI 缺少请求头安全字符规则'
Assert-Contract ($requestIdJava.Contains('[A-Za-z0-9._:-]{1,128}')) 'Java 请求编号规则与 OpenAPI 长度不一致'
Assert-Contract ($idempotencyJava.Contains('[A-Za-z0-9._:-]{16,64}')) 'Java 幂等键规则与 OpenAPI 长度不一致'

$internalOpenApiOperations = [regex]::Matches($openApi, '(?m)^\s+operationId: ([^\r\n]+)\r?\n\s+x-client-scope: INTERNAL\r?$') |
    ForEach-Object { $_.Groups[1].Value.Trim() }
$internalCatalogOperations = $operations | Where-Object ClientScope -eq 'INTERNAL' | ForEach-Object OperationId
Assert-Contract ($internalOpenApiOperations.Count -eq 14) "OpenAPI INTERNAL 操作数量应为 14，实际为 $($internalOpenApiOperations.Count)"
Assert-Contract (($internalOpenApiOperations | Where-Object { $_ -notin $internalCatalogOperations }).Count -eq 0) 'OpenAPI INTERNAL 操作未全部登记到 P0 接口目录'
Assert-Contract (($internalCatalogOperations | Where-Object { $_ -notin $internalOpenApiOperations }).Count -eq 0) 'P0 接口目录存在 OpenAPI 未定义的 INTERNAL 操作'

Write-Output "阶段一契约校验通过：$($operations.Count) 个 P0 操作、$($errors.Count) 个错误码、$($eventNames.Count) 个事件类型。"
