# Shared helpers for dev-up, dev-down, and dev-logs scripts.
# Dot-source this file at the top of each script:
#   . "$PSScriptRoot\_dev-common.ps1"

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$script:Utf8NoBom = [System.Text.UTF8Encoding]::new($false)
[Console]::InputEncoding  = $script:Utf8NoBom
[Console]::OutputEncoding = $script:Utf8NoBom
$OutputEncoding            = $script:Utf8NoBom

# ── Constants ────────────────────────────────────────────────────────────────

$script:ServicePorts = @{
    "gateway-service"       = 8080
    "auth-service"          = 8081
    "content-service"       = 8082
    "review-service"        = 8083
    "search-service"        = 8084
    "file-service"          = 8085
    "notification-service"  = 8086
}

$script:InfrastructureServices = @(
    @{ Name = "mysql";    Port = 3306 },
    @{ Name = "redis";    Port = 6379 },
    @{ Name = "nacos";    Port = 8848 },
    @{ Name = "rabbitmq"; Port = 5672 }
)

$script:SharedModuleDefinitions = @(
    @{ Name = "platform-kernel";      ArtifactId = "platform-kernel";      RelativePomPath = "platform-kernel\pom.xml";      SourceRoots = @("platform-kernel\src\main") },
    @{ Name = "platform-web-support"; ArtifactId = "platform-web-support"; RelativePomPath = "platform-web-support\pom.xml"; SourceRoots = @("platform-web-support\src\main") },
    @{ Name = "platform-events";      ArtifactId = "platform-events";      RelativePomPath = "platform-events\pom.xml";      SourceRoots = @("platform-events\src\main") },
    @{ Name = "auth-contract";        ArtifactId = "auth-contract";        RelativePomPath = "auth-contract\pom.xml";        SourceRoots = @("auth-contract\src\main") },
    @{ Name = "content-contract";     ArtifactId = "content-contract";     RelativePomPath = "content-contract\pom.xml";     SourceRoots = @("content-contract\src\main") },
    @{ Name = "review-contract";      ArtifactId = "review-contract";      RelativePomPath = "review-contract\pom.xml";      SourceRoots = @("review-contract\src\main") }
)

# ── Logging ──────────────────────────────────────────────────────────────────

function Write-Step {
    param([string]$Message)
    Write-Host "==> $Message" -ForegroundColor Cyan
}

function Write-StageTiming {
    param([string]$Name, [double]$Seconds)
    Write-Host ("    {0}: {1:N2}s" -f $Name, $Seconds) -ForegroundColor DarkGray
}

# ── UTF-8 File I/O ──────────────────────────────────────────────────────────

function Get-Utf8Content {
    param(
        [Parameter(Mandatory)][string]$Path,
        [switch]$Raw,
        [int]$Tail = 0
    )
    $params = @{ Path = $Path; Encoding = "UTF8"; ErrorAction = "Stop" }
    if ($Raw)      { $params.Raw  = $true }
    if ($Tail -gt 0) { $params.Tail = $Tail }
    return Get-Content @params
}

function Set-Utf8Content {
    param(
        [Parameter(Mandatory)][string]$Path,
        [Parameter(Mandatory)]$Value,
        [switch]$NoNewline
    )
    $params = @{ Path = $Path; Value = $Value; Encoding = "UTF8" }
    if ($NoNewline) { $params.NoNewline = $true }
    Set-Content @params
}

# ── Network helpers ──────────────────────────────────────────────────────────

function Get-ListeningPidsForPort {
    param([int]$Port)

    # Use the native PowerShell cmdlet instead of shelling out to
    # cmd /c "netstat -ano | findstr ...".  Much faster and more reliable.
    try {
        return @(
            Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue |
                Select-Object -ExpandProperty OwningProcess -Unique
        )
    }
    catch {
        return @()
    }
}

function Get-PrimaryProcessForPort {
    param([int]$Port)
    $pid_ = @(Get-ListeningPidsForPort -Port $Port) | Select-Object -First 1
    if ($null -eq $pid_) { return $null }
    return Get-Process -Id $pid_ -ErrorAction SilentlyContinue
}

function Get-PrimaryListeningPidForService {
    param([string]$Service)
    if (-not $script:ServicePorts.ContainsKey($Service)) { return $null }
    return @(Get-ListeningPidsForPort -Port $script:ServicePorts[$Service]) | Select-Object -First 1
}

function Test-TcpPortOpen {
    param([string]$TargetHost, [int]$Port)
    $client = [System.Net.Sockets.TcpClient]::new()
    try {
        $ar = $client.BeginConnect($TargetHost, $Port, $null, $null)
        if (-not $ar.AsyncWaitHandle.WaitOne(2000, $false)) { return $false }
        $client.EndConnect($ar)
        return $true
    }
    catch { return $false }
    finally { $client.Dispose() }
}

function Wait-ForTcpPort {
    param(
        [string]$Name,
        [string]$TargetHost,
        [int]$Port,
        [int]$TimeoutSeconds = 120
    )
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        if (Test-TcpPortOpen -TargetHost $TargetHost -Port $Port) {
            Write-Step "$Name is accepting TCP connections on ${TargetHost}:$Port"
            return
        }
        Start-Sleep -Seconds 2
    }
    throw "$Name did not open TCP port ${TargetHost}:$Port within $TimeoutSeconds seconds"
}

# ── Process management ───────────────────────────────────────────────────────

function Wait-ForProcessExit {
    param([int]$ProcessId, [int]$TimeoutSeconds = 10)
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        if (-not (Get-Process -Id $ProcessId -ErrorAction SilentlyContinue)) { return }
        Start-Sleep -Milliseconds 300
    }
}

function Stop-ServiceProcesses {
    param([string]$Service, [string]$PidFile)

    $stoppedPids = @()
    if (Test-Path $PidFile) {
        $pidValue = Get-Content -Path $PidFile -Encoding UTF8 -ErrorAction SilentlyContinue
        if (-not [string]::IsNullOrWhiteSpace($pidValue)) {
            $proc = Get-Process -Id $pidValue -ErrorAction SilentlyContinue
            if ($proc) {
                Write-Step "Stopping stale $Service launcher (PID $pidValue)"
                Stop-Process -Id $pidValue -Force -ErrorAction SilentlyContinue
                Wait-ForProcessExit -ProcessId ([int]$pidValue)
                $stoppedPids += [int]$pidValue
            }
        }
        Remove-Item $PidFile -Force -ErrorAction SilentlyContinue
    }

    if ($script:ServicePorts.ContainsKey($Service)) {
        foreach ($portPid in Get-ListeningPidsForPort -Port $script:ServicePorts[$Service]) {
            if ($stoppedPids -contains $portPid) { continue }
            $proc = Get-Process -Id $portPid -ErrorAction SilentlyContinue
            if ($proc) {
                Write-Step "Stopping orphan $Service process on port $($script:ServicePorts[$Service]) (PID $portPid)"
                Stop-Process -Id $portPid -Force -ErrorAction SilentlyContinue
                Wait-ForProcessExit -ProcessId $portPid
            }
        }
    }
}

# ── Paths ────────────────────────────────────────────────────────────────────

function Resolve-AbsolutePath {
    param([string]$Path, [string]$BasePath)
    if ([string]::IsNullOrWhiteSpace($Path)) { return $null }
    if ([System.IO.Path]::IsPathRooted($Path)) {
        return [System.IO.Path]::GetFullPath($Path)
    }
    return [System.IO.Path]::GetFullPath((Join-Path $BasePath $Path))
}

function Get-RepoRelativePath {
    param([string]$BasePath, [string]$Path)
    $baseUri = [Uri](([System.IO.Path]::GetFullPath($BasePath).TrimEnd('\') + '\'))
    $pathUri = [Uri]([System.IO.Path]::GetFullPath($Path))
    return [Uri]::UnescapeDataString($baseUri.MakeRelativeUri($pathUri).ToString()).Replace('/', '\')
}
