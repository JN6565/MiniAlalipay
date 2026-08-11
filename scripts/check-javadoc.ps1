# 检测 Java 文件中缺少 Javadoc 的类型声明和公共方法
$roots = @(
    'd:\newproject\MiniAIalipay\backend\account-center\src\main\java\com\minialalipay\account\application\credit',
    'd:\newproject\MiniAIalipay\backend\account-center\src\main\java\com\minialalipay\account\application\bankcard',
    'd:\newproject\MiniAIalipay\backend\account-center\src\main\java\com\minialalipay\account\application\tcc',
    'd:\newproject\MiniAIalipay\backend\account-center\src\main\java\com\minialalipay\account\domain\credit',
    'd:\newproject\MiniAIalipay\backend\account-center\src\main\java\com\minialalipay\account\domain\bill',
    'd:\newproject\MiniAIalipay\backend\account-center\src\main\java\com\minialalipay\account\domain\repayment',
    'd:\newproject\MiniAIalipay\backend\account-center\src\main\java\com\minialalipay\account\domain\bankcard',
    'd:\newproject\MiniAIalipay\backend\account-center\src\main\java\com\minialalipay\account\interfaces\credit',
    'd:\newproject\MiniAIalipay\backend\account-center\src\main\java\com\minialalipay\account\interfaces\bankcard',
    'd:\newproject\MiniAIalipay\backend\account-center\src\main\java\com\minialalipay\account\interfaces\tcc',
    'd:\newproject\MiniAIalipay\backend\account-center\src\main\java\com\minialalipay\account\infrastructure\credit',
    'd:\newproject\MiniAIalipay\backend\account-center\src\main\java\com\minialalipay\account\infrastructure\bankcard',
    'd:\newproject\MiniAIalipay\backend\business-center\src\main\java\com\minialalipay\business\application\bankcard',
    'd:\newproject\MiniAIalipay\backend\business-center\src\main\java\com\minialalipay\business\interfaces\bankcard',
    'd:\newproject\MiniAIalipay\backend\business-center\src\main\java\com\minialalipay\business\infrastructure\tcc'
)

function HasJavadocAbove($lines, $idx) {
    # 从声明行向上跳过注解与空行，检查是否有 Javadoc 结尾 */
    $i = $idx - 1
    while ($i -ge 0) {
        $t = $lines[$i].Trim()
        if ($t -eq '' -or $t.StartsWith('@')) { $i--; continue }
        return $t.EndsWith('*/') -or $t.StartsWith('//')
    }
    return $false
}

foreach ($root in $roots) {
    if (-not (Test-Path $root)) { continue }
    Get-ChildItem -Recurse -File -Filter *.java $root | ForEach-Object {
        $lines = Get-Content $_.FullName
        $short = $_.FullName.Replace('d:\newproject\MiniAIalipay\backend\', '')
        for ($n = 0; $n -lt $lines.Count; $n++) {
            $line = $lines[$n]
            # 类型声明（含 package-info 跳过）
            if ($line -match '^\s*public\s+(final\s+|abstract\s+)?(class|interface|enum|record)\s+(\w+)') {
                if ($Matches[3] -ne 'package-info' -and -not (HasJavadocAbove $lines $n)) {
                    Write-Output "TYPE $short : $($n+1) $($Matches[2]) $($Matches[3])"
                }
            }
            # 公共方法（排除构造器：方法名与类名相同时跳过，用括号前名称简单判断）
            elseif ($line -match '^\s{4}public\s+[\w\<\>\[\],\.\s]+\s+(\w+)\s*\(' ) {
                $name = $Matches[1]
                $fileName = $_.BaseName
                if ($name -eq $fileName) { continue }  # 构造器
                if ($name -match '^(get|set|is)[A-Z]|^(code|message|httpStatus|toString|hashCode|equals)$') { continue }  # getter/setter 与标准覆写豁免
                if ($_.FullName -match '\\po\\') { continue }  # PO 数据类豁免
                if (-not (HasJavadocAbove $lines $n)) {
                    Write-Output "METHOD $short : $($n+1) $name"
                }
            }
        }
    }
}
