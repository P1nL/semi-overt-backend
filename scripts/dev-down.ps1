param(
    [switch]$RemoveVolumes
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
[Console]::InputEncoding = [System.Text.UTF8Encoding]::new($false)
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)
$OutputEncoding = [System.Text.UTF8Encoding]::new($false)

function Write-Step {
    param([string]$Message)
    Write-Host "==> $Message" -ForegroundColor Cyan
}

$servicePorts = @{
    "gateway-service" = 8080
    "auth-service" = 8081
    "content-service" = 8082
    "review-service" = 8083
    "search-service" = 8084
    "file-service" = 8085
    "notification-service" = 8086
}

function Get-ListeningPidsForPort {
    param([int]$Port)

    $lines = cmd /c "netstat -ano -p tcp | findstr LISTENING | findstr :$Port"
    $pids = @()

    foreach ($line in $lines) {
        if ($line -match "LISTENING\s+(\d+)\s*$") {
            $pids += [int]$Matches[1]
        }
    }

    return $pids | Select-Object -Unique
}

$repoRoot = Split-Path -Parent $PSScriptRoot
$composePath = Join-Path $repoRoot "docker-compose.yml"
$pidRoot = Join-Path $repoRoot ".codex-runtime\pids"

if (Test-Path $pidRoot) {
    Get-ChildItem -Path $pidRoot -Filter *.pid | ForEach-Object {
        $service = [System.IO.Path]::GetFileNameWithoutExtension($_.Name)
        $pidValue = Get-Content $_.FullName -ErrorAction SilentlyContinue
        if (-not [string]::IsNullOrWhiteSpace($pidValue)) {
            $process = Get-Process -Id $pidValue -ErrorAction SilentlyContinue
            if ($process) {
                Write-Step "Stopping $service (PID $pidValue)"
                Stop-Process -Id $pidValue -Force
            }
        }
        Remove-Item $_.FullName -Force -ErrorAction SilentlyContinue
    }
}

foreach ($service in $servicePorts.Keys) {
    foreach ($portPid in Get-ListeningPidsForPort -Port $servicePorts[$service]) {
        $process = Get-Process -Id $portPid -ErrorAction SilentlyContinue
        if ($process) {
            Write-Step "Stopping orphan $service on port $($servicePorts[$service]) (PID $portPid)"
            Stop-Process -Id $portPid -Force -ErrorAction SilentlyContinue
        }
    }
}

if (-not (Test-Path $composePath)) {
    throw "Cannot find docker-compose.yml at $composePath"
}

$composeArgs = @("compose", "down")
if ($RemoveVolumes) {
    $composeArgs += "-v"
}

Write-Step ("Stopping Docker middleware" + ($(if ($RemoveVolumes) { " and removing volumes" } else { "" })))

Push-Location $repoRoot
try {
    docker @composeArgs
    if ($LASTEXITCODE -ne 0) {
        throw "docker compose down failed with exit code $LASTEXITCODE"
    }
}
finally {
    Pop-Location
}
