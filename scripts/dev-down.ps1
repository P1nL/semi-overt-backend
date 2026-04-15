param(
    [switch]$RemoveVolumes
)

. "$PSScriptRoot\_dev-common.ps1"

$repoRoot    = Split-Path -Parent $PSScriptRoot
$composePath = Join-Path $repoRoot "docker-compose.yml"
$pidRoot     = Join-Path $repoRoot ".codex-runtime\pids"

# Stop services tracked by PID files
if (Test-Path $pidRoot) {
    Get-ChildItem -Path $pidRoot -Filter *.pid | ForEach-Object {
        $service  = [System.IO.Path]::GetFileNameWithoutExtension($_.Name)
        $pidValue = Get-Content -Path $_.FullName -Encoding UTF8 -ErrorAction SilentlyContinue
        if (-not [string]::IsNullOrWhiteSpace($pidValue)) {
            $proc = Get-Process -Id $pidValue -ErrorAction SilentlyContinue
            if ($proc) {
                Write-Step "Stopping $service (PID $pidValue)"
                Stop-Process -Id $pidValue -Force
            }
        }
        Remove-Item $_.FullName -Force -ErrorAction SilentlyContinue
    }
}

# Kill any orphaned processes still holding service ports
foreach ($service in $script:ServicePorts.Keys) {
    foreach ($portPid in Get-ListeningPidsForPort -Port $script:ServicePorts[$service]) {
        $proc = Get-Process -Id $portPid -ErrorAction SilentlyContinue
        if ($proc) {
            Write-Step "Stopping orphan $service on port $($script:ServicePorts[$service]) (PID $portPid)"
            Stop-Process -Id $portPid -Force -ErrorAction SilentlyContinue
        }
    }
}

# Tear down Docker infrastructure
if (-not (Test-Path $composePath)) {
    throw "Cannot find docker-compose.yml at $composePath"
}

$composeArgs = @("compose", "down")
if ($RemoveVolumes) { $composeArgs += "-v" }
$composeArgs += "--remove-orphans"

Write-Step ("Stopping Docker middleware" + $(if ($RemoveVolumes) { " and removing volumes" } else { "" }))

Push-Location $repoRoot
try {
    docker @composeArgs
    if ($LASTEXITCODE -ne 0) { throw "docker compose down failed (exit $LASTEXITCODE)" }
    docker compose --profile ops-ui down --remove-orphans
    if ($LASTEXITCODE -ne 0) { throw "docker compose --profile ops-ui down failed (exit $LASTEXITCODE)" }
}
finally { Pop-Location }
