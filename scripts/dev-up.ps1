# Local Windows development launcher. Not intended for Linux/cloud deployment.
param(
    [string]$RunProfile = "local",
    [string[]]$Services = @(
        "auth-service",
        "content-service",
        "review-service",
        "search-service",
        "file-service",
        "notification-service",
        "gateway-service"
    ),
    [switch]$SkipDocker
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
[Console]::InputEncoding = [System.Text.UTF8Encoding]::new($false)
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)
$OutputEncoding = [System.Text.UTF8Encoding]::new($false)

if (-not $env:NACOS_SERVER_ADDR) {
    $env:NACOS_SERVER_ADDR = "127.0.0.1:8848"
}

if (-not $env:NACOS_NAMESPACE) {
    # Use the actual dev namespace ID from Nacos instead of the display name.
    $env:NACOS_NAMESPACE = "6b1b920b-c784-475e-80f8-b2fcebf24dbf"
}

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

function Stop-ServiceProcesses {
    param(
        [string]$Service,
        [string]$PidFile
    )

    $stoppedPids = @()

    if (Test-Path $PidFile) {
        $pidValue = Get-Content $PidFile -ErrorAction SilentlyContinue
        if (-not [string]::IsNullOrWhiteSpace($pidValue)) {
            $process = Get-Process -Id $pidValue -ErrorAction SilentlyContinue
            if ($process) {
                Write-Step "Stopping stale $Service launcher (PID $pidValue)"
                Stop-Process -Id $pidValue -Force -ErrorAction SilentlyContinue
                $stoppedPids += [int]$pidValue
            }
        }

        Remove-Item $PidFile -Force -ErrorAction SilentlyContinue
    }

    if ($servicePorts.ContainsKey($Service)) {
        foreach ($portPid in Get-ListeningPidsForPort -Port $servicePorts[$Service]) {
            if ($stoppedPids -contains $portPid) {
                continue
            }

            $portProcess = Get-Process -Id $portPid -ErrorAction SilentlyContinue
            if ($portProcess) {
                Write-Step "Stopping orphan $Service process on port $($servicePorts[$Service]) (PID $portPid)"
                Stop-Process -Id $portPid -Force -ErrorAction SilentlyContinue
            }
        }
    }
}

function Reset-LogFile {
    param([string]$Path)

    if (-not (Test-Path $Path)) {
        return
    }

    try {
        Remove-Item $Path -Force
    }
    catch {
        $timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
        $rotatedPath = "$Path.$timestamp"
        Move-Item $Path $rotatedPath -Force
    }
}

function Resolve-MavenCommand {
    param([string]$RepoRoot)

    $candidates = @(
        $env:MAVEN_CMD,
        (Join-Path $RepoRoot ".cache\maven\apache-maven-3.9.12\bin\mvn.cmd"),
        "mvn.cmd",
        (Join-Path $RepoRoot "mvnw.cmd")
    ) | Where-Object { -not [string]::IsNullOrWhiteSpace($_) }

    foreach ($candidate in $candidates) {
        if (Test-Path $candidate) {
            return (Resolve-Path $candidate).Path
        }

        $command = Get-Command $candidate -ErrorAction SilentlyContinue
        if ($command) {
            return $command.Source
        }
    }

    throw "Cannot resolve Maven command. Set MAVEN_CMD or install Maven."
}

function Resolve-AbsolutePath {
    param(
        [string]$Path,
        [string]$BasePath
    )

    if ([string]::IsNullOrWhiteSpace($Path)) {
        return $null
    }

    if ([System.IO.Path]::IsPathRooted($Path)) {
        return [System.IO.Path]::GetFullPath($Path)
    }

    return [System.IO.Path]::GetFullPath((Join-Path $BasePath $Path))
}

function Get-DefaultMavenRepoLocal {
    param([string]$RepoRoot)

    return (Join-Path $RepoRoot ".cache\m2\repository")
}

function Ensure-MavenRuntimeConfig {
    param(
        [string]$RepoRoot,
        [string]$RuntimeRoot
    )

    $repoLocal = if (-not [string]::IsNullOrWhiteSpace($env:MAVEN_REPO_LOCAL)) {
        Resolve-AbsolutePath -Path $env:MAVEN_REPO_LOCAL -BasePath $RepoRoot
    }
    else {
        Get-DefaultMavenRepoLocal -RepoRoot $RepoRoot
    }

    New-Item -ItemType Directory -Force -Path $repoLocal | Out-Null

    $settingsPath = if (-not [string]::IsNullOrWhiteSpace($env:MAVEN_SETTINGS)) {
        Resolve-AbsolutePath -Path $env:MAVEN_SETTINGS -BasePath $RepoRoot
    }
    else {
        Join-Path $RuntimeRoot "maven-settings.xml"
    }

    if (-not [string]::IsNullOrWhiteSpace($env:MAVEN_SETTINGS)) {
        if (-not (Test-Path $settingsPath)) {
            throw "Cannot find Maven settings file: $settingsPath"
        }
    }
    else {
        $escapedRepoLocal = [System.Security.SecurityElement]::Escape($repoLocal)
        $settingsContent = @"
<settings xmlns="http://maven.apache.org/SETTINGS/1.2.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.2.0 https://maven.apache.org/xsd/settings-1.2.0.xsd">
  <localRepository>$escapedRepoLocal</localRepository>
</settings>
"@
        [System.IO.File]::WriteAllText(
            $settingsPath,
            $settingsContent,
            [System.Text.UTF8Encoding]::new($false)
        )
    }

    if ([string]::IsNullOrWhiteSpace($env:MAVEN_SETTINGS)) {
        $env:MAVEN_SETTINGS = $settingsPath
    }
    if ([string]::IsNullOrWhiteSpace($env:MAVEN_REPO_LOCAL)) {
        $env:MAVEN_REPO_LOCAL = $repoLocal
    }

    return @{
        SettingsPath = $settingsPath
        RepoLocal = $repoLocal
    }
}

function Invoke-Maven {
    param(
        [string]$MavenCommand,
        [string[]]$Arguments,
        [string]$WorkingDirectory
    )

    Push-Location $WorkingDirectory
    try {
        & $MavenCommand @Arguments

        if ($LASTEXITCODE -ne 0) {
            throw "Maven command failed with exit code $LASTEXITCODE"
        }
    }
    finally {
        Pop-Location
    }
}

function Get-MavenCommonArguments {
    param(
        [string]$SettingsPath,
        [string]$RepoLocal
    )

    $arguments = @("-s", $SettingsPath)

    if (-not [string]::IsNullOrWhiteSpace($env:MAVEN_REPO_LOCAL)) {
        $arguments += "-Dmaven.repo.local=$RepoLocal"
    }

    return $arguments
}

function Get-ServiceCommand {
    param(
        [string]$MavenCommand,
        [string]$RepoRoot,
        [string]$Service,
        [string]$RunProfile
    )

    $modulePom = Join-Path $RepoRoot "$Service\pom.xml"
    if (-not (Test-Path $modulePom)) {
        throw "Cannot find module pom: $modulePom"
    }

    return @{
        MavenCommand = $MavenCommand
        ModulePom = $modulePom
        RunProfile = $RunProfile
    }
}

function ConvertTo-SingleQuotedPowerShellLiteral {
    param([string]$Value)

    return "'" + $Value.Replace("'", "''") + "'"
}

$repoRoot = Split-Path -Parent $PSScriptRoot
$composePath = Join-Path $repoRoot "docker-compose.yml"
$runtimeRoot = Join-Path $repoRoot ".codex-runtime"
$pidRoot = Join-Path $runtimeRoot "pids"
$logRoot = Join-Path $runtimeRoot "logs"

New-Item -ItemType Directory -Force -Path $runtimeRoot | Out-Null
New-Item -ItemType Directory -Force -Path $pidRoot | Out-Null
New-Item -ItemType Directory -Force -Path $logRoot | Out-Null

$mavenRuntimeConfig = Ensure-MavenRuntimeConfig -RepoRoot $repoRoot -RuntimeRoot $runtimeRoot
$mavenCommand = Resolve-MavenCommand -RepoRoot $repoRoot
$mavenCommonArguments = @(Get-MavenCommonArguments `
    -SettingsPath $mavenRuntimeConfig.SettingsPath `
    -RepoLocal $mavenRuntimeConfig.RepoLocal)

Write-Step "Using Maven command: $mavenCommand"
Write-Step "Using Maven settings: $($mavenRuntimeConfig.SettingsPath)"
Write-Step "Using Maven local repository: $($mavenRuntimeConfig.RepoLocal)"

if (-not $SkipDocker) {
    if (-not (Test-Path $composePath)) {
        throw "Cannot find docker-compose.yml at $composePath"
    }

    Write-Step "Starting Docker middleware from docker-compose.yml"
    Push-Location $repoRoot
    try {
        docker compose up -d
        if ($LASTEXITCODE -ne 0) {
            throw "docker compose up -d failed with exit code $LASTEXITCODE"
        }
    }
    finally {
        Pop-Location
    }
}
else {
    Write-Step "Skipping Docker startup"
}

Write-Step "Installing parent POM and common module into local Maven repository"
Invoke-Maven -MavenCommand $mavenCommand `
    -Arguments ($mavenCommonArguments + @("-N", "install")) `
    -WorkingDirectory $repoRoot
Invoke-Maven -MavenCommand $mavenCommand `
    -Arguments ($mavenCommonArguments + @("-f", (Join-Path $repoRoot "common\pom.xml"), "install", "-DskipTests")) `
    -WorkingDirectory $repoRoot

foreach ($service in $Services) {
    $moduleDir = Join-Path $repoRoot $service
    if (-not (Test-Path $moduleDir)) {
        throw "Cannot find module directory: $moduleDir"
    }

    $pidFile = Join-Path $pidRoot "$service.pid"
    if (Test-Path $pidFile) {
        $existingPid = Get-Content $pidFile -ErrorAction SilentlyContinue
        if ($existingPid) {
            $existingProcess = Get-Process -Id $existingPid -ErrorAction SilentlyContinue
            $listeningPids = if ($servicePorts.ContainsKey($service)) {
                @(Get-ListeningPidsForPort -Port $servicePorts[$service])
            }
            else {
                @()
            }

            if ($existingProcess -and (@($listeningPids)).Count -gt 0) {
                Write-Step "$service is already running with PID $existingPid"
                continue
            }
        }
    }

    Stop-ServiceProcesses -Service $service -PidFile $pidFile

    $stdoutLog = Join-Path $logRoot "$service.out.log"
    $stderrLog = Join-Path $logRoot "$service.err.log"
    Reset-LogFile -Path $stdoutLog
    Reset-LogFile -Path $stderrLog

    $command = Get-ServiceCommand -MavenCommand $mavenCommand -RepoRoot $repoRoot -Service $service -RunProfile $RunProfile
    Write-Step "Starting $service"

    $serviceArguments = $mavenCommonArguments + @(
        "-f",
        $command.ModulePom,
        "spring-boot:run",
        "-Dspring-boot.run.profiles=$($command.RunProfile)"
    )
    $encodedArgumentArray = (($serviceArguments | ForEach-Object {
        ConvertTo-SingleQuotedPowerShellLiteral -Value $_
    }) -join ", ")
    $encodedScript = "& $(ConvertTo-SingleQuotedPowerShellLiteral -Value $command.MavenCommand) @($encodedArgumentArray)"

    $process = Start-Process -FilePath "powershell.exe" `
        -ArgumentList "-NoLogo", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-Command", $encodedScript `
        -WorkingDirectory $repoRoot `
        -WindowStyle Hidden `
        -RedirectStandardOutput $stdoutLog `
        -RedirectStandardError $stderrLog `
        -PassThru

    Set-Content -Path $pidFile -Value $process.Id -NoNewline
}

Write-Step "Started services: $($Services -join ', ')"
Write-Host "Logs: $logRoot" -ForegroundColor Green
Write-Host "PIDs: $pidRoot" -ForegroundColor Green
