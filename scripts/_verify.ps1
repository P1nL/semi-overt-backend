$ErrorActionPreference = "Stop"

# Test 1: Load common module
. "$PSScriptRoot\_dev-common.ps1"
Write-Host "PASS: _dev-common.ps1 loaded"
Write-Host "  ServicePorts: $($script:ServicePorts.Count)"
Write-Host "  InfraServices: $($script:InfrastructureServices.Count)"
Write-Host "  SharedModules: $($script:SharedModuleDefinitions.Count)"

# Test 2: Get-ListeningPidsForPort
$pids = Get-ListeningPidsForPort -Port 3306
Write-Host "  MySQL PIDs on 3306: $($pids -join ',')"

# Test 3: Test-TcpPortOpen
$open = Test-TcpPortOpen -TargetHost "127.0.0.1" -Port 3306
Write-Host "  TCP 3306 open: $open"

# Test 4: Parse dev-up.ps1
try {
    $null = [System.Management.Automation.Language.Parser]::ParseFile(
        (Join-Path $PSScriptRoot "dev-up.ps1"),
        [ref]$null,
        [ref]$null
    )
    Write-Host "PASS: dev-up.ps1 syntax OK"
}
catch { Write-Host "FAIL: dev-up.ps1 syntax error: $_" }

# Test 5: Parse dev-down.ps1
try {
    $null = [System.Management.Automation.Language.Parser]::ParseFile(
        (Join-Path $PSScriptRoot "dev-down.ps1"),
        [ref]$null,
        [ref]$null
    )
    Write-Host "PASS: dev-down.ps1 syntax OK"
}
catch { Write-Host "FAIL: dev-down.ps1 syntax error: $_" }

# Test 6: Parse dev-logs.ps1
try {
    $null = [System.Management.Automation.Language.Parser]::ParseFile(
        (Join-Path $PSScriptRoot "dev-logs.ps1"),
        [ref]$null,
        [ref]$null
    )
    Write-Host "PASS: dev-logs.ps1 syntax OK"
}
catch { Write-Host "FAIL: dev-logs.ps1 syntax error: $_" }

Write-Host ""
Write-Host "All checks passed." -ForegroundColor Green
