# 检查 H5 页面文件前 20 行是否包含 JSDoc 页面注释
$pages = @('BankCardBind','BankCardAdd','BankCardDetail','BankCardWithdraw','BankCardBills','CreditBillDetail','Credit','BankCards','BankCardRecharge','CreditBills','CreditRepay')
foreach ($p in $pages) {
    $f = 'd:\newproject\MiniAIalipay\frontend-h5\src\pages\h5\' + $p + '\index.tsx'
    if (-not (Test-Path $f)) { Write-Output "$p MISSING"; continue }
    $head = (Get-Content $f -TotalCount 25) -join "`n"
    if ($head -match '/\*\*') { Write-Output "$p HAS-JSDOC" } else { Write-Output "$p NO-JSDOC" }
}
