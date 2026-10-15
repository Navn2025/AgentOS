$ErrorActionPreference = 'Stop'
$agentosRoot = Split-Path -Parent $PSScriptRoot
$agentosPython = Join-Path $agentosRoot '.venv\Scripts\python.exe'
$agentosLogs = Join-Path $agentosRoot '.local'
New-Item -ItemType Directory -Path $agentosLogs -Force | Out-Null
if (-not (Test-Path -LiteralPath $agentosPython)) { throw 'Run scripts/setup.ps1 first.' }
$agentosPids = @{}
foreach ($agentosPort in @(8000,5173)) {
    $agentosListener = Get-NetTCPConnection -LocalPort $agentosPort -State Listen -ErrorAction SilentlyContinue
    if ($agentosListener) { Write-Host "Port $agentosPort is already listening. Keeping the existing process."; continue }
    if ($agentosPort -eq 8000) {
        $agentosProc = Start-Process -FilePath $agentosPython -ArgumentList @('-m','uvicorn','app.main:app','--host','127.0.0.1','--port','8000') -WorkingDirectory (Join-Path $agentosRoot 'backend') -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $agentosLogs 'backend.log') -RedirectStandardError (Join-Path $agentosLogs 'backend-error.log')
    } else {
        $agentosNode = (Get-Command node.exe).Source
        $agentosVite = Join-Path $agentosRoot 'frontend\node_modules\vite\bin\vite.js'
        $agentosProc = Start-Process -FilePath $agentosNode -ArgumentList @("`"$agentosVite`"",'--host','127.0.0.1','--port','5173','--strictPort') -WorkingDirectory (Join-Path $agentosRoot 'frontend') -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $agentosLogs 'frontend.log') -RedirectStandardError (Join-Path $agentosLogs 'frontend-error.log')
    }
    $agentosPids["$agentosPort-launcher"] = @{ Id=$agentosProc.Id; StartedTicks=$agentosProc.StartTime.ToUniversalTime().Ticks.ToString() }
}
if ($agentosPids.Count) {
    $agentosPidPath = Join-Path $agentosLogs 'processes.json'
    $agentosPrevious = @{}
    if (Test-Path -LiteralPath $agentosPidPath) { (Get-Content -LiteralPath $agentosPidPath -Raw | ConvertFrom-Json).PSObject.Properties | ForEach-Object { $agentosPrevious[$_.Name] = $_.Value } }
    foreach ($agentosKey in $agentosPids.Keys) { $agentosPrevious[$agentosKey] = $agentosPids[$agentosKey] }
    $agentosPrevious | ConvertTo-Json | Set-Content -LiteralPath $agentosPidPath
}
for ($agentosAttempt=0; $agentosAttempt -lt 20; $agentosAttempt++) {
    try {
        $agentosHealth = Invoke-RestMethod -Uri 'http://127.0.0.1:8000/api/health' -TimeoutSec 2
        $agentosWeb = Invoke-WebRequest -Uri 'http://127.0.0.1:5173/' -TimeoutSec 2 -UseBasicParsing
        if ($agentosHealth.status -eq 'ok' -and $agentosWeb.StatusCode -eq 200) {
            foreach ($agentosPort in @(8000,5173)) {
                if ($agentosPids.ContainsKey("$agentosPort-launcher")) {
                    $agentosWorkerId = (Get-NetTCPConnection -LocalPort $agentosPort -State Listen | Select-Object -First 1).OwningProcess
                    $agentosWorker = Get-Process -Id $agentosWorkerId
                    $agentosPrevious["$agentosPort"] = @{ Id=$agentosWorkerId; StartedTicks=$agentosWorker.StartTime.ToUniversalTime().Ticks.ToString() }
                }
            }
            if ($agentosPids.Count) { $agentosPrevious | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $agentosPidPath }
            Write-Host 'AgentOS ready: http://localhost:5173'
            Write-Host 'API documentation: http://localhost:8000/docs'
            exit 0
        }
    } catch { Start-Sleep -Seconds 1 }
}
throw 'Startup did not pass health checks. Read .local/backend-error.log and .local/frontend-error.log.'
