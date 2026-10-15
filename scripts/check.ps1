param([switch]$IncludeMcp)
$ErrorActionPreference = 'Stop'
$agentosRoot = Split-Path -Parent $PSScriptRoot
$agentosPython = Join-Path $agentosRoot '.venv\Scripts\python.exe'
Push-Location -LiteralPath (Join-Path $agentosRoot 'backend')
try {
    foreach ($agentosCheck in @('check_services.py','check_api.py','check_agents.py','check_laptop.py','check_research.py')) {
        & $agentosPython $agentosCheck
        if ($LASTEXITCODE -ne 0) { throw "Failed: $agentosCheck" }
    }
    if ($IncludeMcp) { & $agentosPython check_mcp.py; if ($LASTEXITCODE -ne 0) { throw 'MCP check failed.' } }
} finally { Pop-Location }
Push-Location -LiteralPath (Join-Path $agentosRoot 'frontend')
try {
    & npm.cmd test
    if ($LASTEXITCODE -ne 0) { throw 'Frontend acceptance checks failed.' }
    & npm.cmd run build
    if ($LASTEXITCODE -ne 0) { throw 'Production build failed.' }
} finally { Pop-Location }
Write-Host 'All selected checks passed.'
