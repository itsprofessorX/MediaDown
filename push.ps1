param([string]$m = "update $(Get-Date -Format 'yyyy-MM-dd HH:mm')")
Remove-Item Env:GIT_DIR -ErrorAction SilentlyContinue
Set-Location $PSScriptRoot
git add -A
git add -f app/debug.keystore
git commit -m $m
git push origin HEAD
