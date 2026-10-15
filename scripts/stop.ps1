$ErrorActionPreference = 'Stop'
$agentosRoot = Split-Path -Parent $PSScriptRoot
$agentosPidPath = Join-Path $agentosRoot '.local\processes.json'
if (-not (Test-Path -LiteralPath $agentosPidPath)) { Write-Host 'No script-managed processes recorded.'; exit }
$agentosPids = (Get-Content -LiteralPath $agentosPidPath -Raw | ConvertFrom-Json).PSObject.Properties | ForEach-Object { $_.Value }
foreach ($agentosRecord in $agentosPids) {
    $agentosId = $agentosRecord.Id
    $agentosProc = Get-CimInstance Win32_Process -Filter "ProcessId=$agentosId" -ErrorAction SilentlyContinue
    if (-not $agentosProc) { continue }
    $agentosActual = Get-Process -Id $agentosId -ErrorAction SilentlyContinue
    if (-not $agentosActual -or $agentosActual.StartTime.ToUniversalTime().Ticks.ToString() -ne [string]$agentosRecord.StartedTicks) {
        Write-Host "Skipped reused process ID $agentosId."; continue
    }
    if ($agentosProc.CommandLine -like '*app.main:app*' -or $agentosProc.CommandLine -like '*AgentOS*frontend*vite*') {
        Stop-Process -Id $agentosId
        Write-Host "Stopped AgentOS process $agentosId."
    } else { Write-Host "Skipped reused process ID $agentosId." }
}
Remove-Item -LiteralPath $agentosPidPath
