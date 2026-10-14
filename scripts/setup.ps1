param([switch]$ConfigureDatabase)
$ErrorActionPreference = 'Stop'
$agentosRoot = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $agentosRoot
foreach ($agentosPort in @(8000,5173)) {
    $agentosListener = Get-NetTCPConnection -LocalPort $agentosPort -State Listen -ErrorAction SilentlyContinue
    if ($agentosListener) { throw "Port $agentosPort is in use. Stop AgentOS before reinstalling dependencies; Windows locks loaded native modules." }
}
if (-not (Test-Path -LiteralPath '.venv\Scripts\python.exe')) {
    if (Get-Command uv -ErrorAction SilentlyContinue) { & uv venv --python 3.12 .venv }
    else { & py -3.12 -m venv .venv }
    if ($LASTEXITCODE -ne 0) { throw 'Python environment setup failed.' }
}
$agentosPython = Join-Path $agentosRoot '.venv\Scripts\python.exe'
if (Get-Command uv -ErrorAction SilentlyContinue) { & uv pip sync --python $agentosPython backend\requirements.lock }
else { & $agentosPython -m pip install -r backend\requirements.lock }
if ($LASTEXITCODE -ne 0) { throw 'Backend dependency installation failed.' }
if ($ConfigureDatabase -or -not (Test-Path -LiteralPath 'backend\.env')) {
    & $agentosPython backend\configure_database.py
    if ($LASTEXITCODE -ne 0) { throw 'Database configuration failed.' }
}
Push-Location -LiteralPath 'backend'
try { & $agentosPython -m app.db; if ($LASTEXITCODE -ne 0) { throw 'SQL migration failed.' } }
finally { Pop-Location }
Push-Location -LiteralPath 'frontend'
try { & npm.cmd ci; if ($LASTEXITCODE -ne 0) { throw 'Frontend dependency installation failed.' } }
finally { Pop-Location }
Write-Host 'Setup complete. Add model API keys to backend/.env, then run .\scripts\start.ps1.'
Write-Host 'Optional demo data: .\.venv\Scripts\python.exe backend\seed.py'
