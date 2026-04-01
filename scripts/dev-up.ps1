# Local Windows development launcher. Not intended for Linux/cloud deployment.
param(
    [string]$RunProfile = "local",
    [ValidateSet("stable", "fast")]
    [string]$StartupMode = "stable",
    [string[]]$Services = @(
        "auth-service",
        "content-service",
        "review-service",
        "search-service",
        "notification-service",
        "file-service",
        "gateway-service"
    ),
    [switch]$SkipDocker,
    [Alias("ForceRebuildCommon")]
    [switch]$ForceRebuildSharedModules,
    [switch]$RestartServices
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

function Write-StageTiming {
    param(
        [string]$Name,
        [double]$Seconds
    )

    Write-Host ("    {0}: {1:N2}s" -f $Name, $Seconds) -ForegroundColor DarkGray
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

$infrastructureServices = @(
    @{ Name = "mysql"; Port = 3306 },
    @{ Name = "redis"; Port = 6379 },
    @{ Name = "nacos"; Port = 8848 },
    @{ Name = "rabbitmq"; Port = 5672 }
)

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

function Get-PrimaryProcessForPort {
    param([int]$Port)

    $portPid = @(Get-ListeningPidsForPort -Port $Port) | Select-Object -First 1
    if ($null -eq $portPid) {
        return $null
    }

    return Get-Process -Id $portPid -ErrorAction SilentlyContinue
}

function Get-PrimaryListeningPidForService {
    param([string]$Service)

    if (-not $servicePorts.ContainsKey($Service)) {
        return $null
    }

    return @(Get-ListeningPidsForPort -Port $servicePorts[$Service]) | Select-Object -First 1
}

function Wait-ForProcessExit {
    param(
        [int]$ProcessId,
        [int]$TimeoutSeconds = 10
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        $process = Get-Process -Id $ProcessId -ErrorAction SilentlyContinue
        if (-not $process) {
            return
        }

        Start-Sleep -Milliseconds 300
    }
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
                Wait-ForProcessExit -ProcessId ([int]$pidValue)
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
                Wait-ForProcessExit -ProcessId $portPid
            }
        }
    }
}

function Reset-LogFile {
    param(
        [string]$Path,
        [int]$RetryCount = 40
    )

    if (-not (Test-Path $Path)) {
        return
    }

    for ($attempt = 1; $attempt -le $RetryCount; $attempt++) {
        try {
            Remove-Item $Path -Force
            return
        }
        catch {
            try {
                $timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
                $rotatedPath = "$Path.$timestamp.$attempt"
                Move-Item $Path $rotatedPath -Force
                return
            }
            catch {
                if ($attempt -eq $RetryCount) {
                    throw "Cannot reset log file $Path because it is still in use by another process."
                }

                Start-Sleep -Milliseconds 500
            }
        }
    }
}

function Invoke-HttpGet {
    param([string]$Uri)

    return Invoke-WebRequest -Uri $Uri -Method Get -TimeoutSec 5
}

function Test-ServiceHealthEndpoint {
    param([int]$Port)

    try {
        $response = Invoke-RestMethod -Uri "http://127.0.0.1:$Port/actuator/health" -Method Get -TimeoutSec 5
        return $response.status -eq "UP"
    }
    catch {
        return $false
    }
}

function Test-ServiceInfoEndpoint {
    param([int]$Port)

    try {
        $response = Invoke-HttpGet -Uri "http://127.0.0.1:$Port/actuator/info"
        return $response.StatusCode -ge 200 -and $response.StatusCode -lt 300
    }
    catch {
        return $false
    }
}

function Test-TcpPortOpen {
    param(
        [string]$TargetHost,
        [int]$Port
    )

    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $asyncResult = $client.BeginConnect($TargetHost, $Port, $null, $null)
        $connected = $asyncResult.AsyncWaitHandle.WaitOne(2000, $false)
        if (-not $connected) {
            return $false
        }

        $client.EndConnect($asyncResult)
        return $true
    }
    catch {
        return $false
    }
    finally {
        $client.Dispose()
    }
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

function Wait-ForServiceReadiness {
    param(
        [string]$Service,
        [int]$Port,
        [int]$TimeoutSeconds = 180,
        $Process = $null,
        [string]$StdoutLog = "",
        [string]$StderrLog = ""
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    $healthReady = $false
    $infoReady = $false

    while ((Get-Date) -lt $deadline) {
        $listening = (@(Get-ListeningPidsForPort -Port $Port)).Count -gt 0
        if ($listening) {
            $healthReady = Test-ServiceHealthEndpoint -Port $Port
            $infoReady = Test-ServiceInfoEndpoint -Port $Port
            if ($healthReady -and $infoReady) {
                Write-Step "$Service is ready on port $Port"
                return
            }
        }

        if ($Process -and -not $listening) {
            try {
                $Process.Refresh()
            }
            catch {
                # Ignore launcher process state. The actual Java service may outlive the wrapper.
            }
        }

        Start-Sleep -Seconds 2
    }

    $missingChecks = @()
    if (-not ((@(Get-ListeningPidsForPort -Port $Port)).Count -gt 0)) {
        $missingChecks += "port $Port is not listening"
    }
    if (-not $healthReady) {
        $missingChecks += "/actuator/health is not UP"
    }
    if (-not $infoReady) {
        $missingChecks += "/actuator/info is not reachable"
    }

    throw "$Service did not become ready within $TimeoutSeconds seconds: $($missingChecks -join '; ')"
}

function Get-DockerServiceContainerId {
    param(
        [string]$RepoRoot,
        [string]$Service
    )

    Push-Location $RepoRoot
    try {
        $output = docker compose ps -q $Service
        if ($null -eq $output) {
            return ""
        }

        return ($output | Out-String).Trim()
    }
    finally {
        Pop-Location
    }
}

function Get-DockerServiceHealth {
    param([string]$ContainerId)

    if ([string]::IsNullOrWhiteSpace($ContainerId)) {
        return ""
    }

    return (docker inspect --format "{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}" $ContainerId).Trim()
}

function Get-DockerServiceLogs {
    param(
        [string]$RepoRoot,
        [string]$Service,
        [int]$Tail = 20
    )

    Push-Location $RepoRoot
    try {
        $output = docker compose logs --tail $Tail $Service 2>&1
        if ($null -eq $output) {
            return ""
        }

        return ($output | Out-String).Trim()
    }
    finally {
        Pop-Location
    }
}

function Get-InfrastructureRecoveryHint {
    param(
        [string]$Service,
        [string]$Logs
    )

    if ($Service -eq "redis" -and $Logs -match "Can't handle RDB format version") {
        return "Redis volume contains data created by a newer Redis version. For this local demo stack, remove the Redis container and volume, then retry. Example: docker compose rm -sf redis; docker volume rm now-demo_redis-data"
    }

    return ""
}

function Wait-ForDockerServiceHealth {
    param(
        [string]$RepoRoot,
        [string]$Service,
        [int]$TimeoutSeconds = 180
    )

    Write-Step "Waiting for infrastructure service $Service"
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)

    while ((Get-Date) -lt $deadline) {
        $containerId = Get-DockerServiceContainerId -RepoRoot $RepoRoot -Service $Service
        $health = Get-DockerServiceHealth -ContainerId $containerId

        if ($health -eq "healthy" -or $health -eq "running") {
            return
        }
        if ($health -eq "unhealthy" -or $health -eq "exited" -or $health -eq "dead") {
            $logs = Get-DockerServiceLogs -RepoRoot $RepoRoot -Service $Service
            $hint = Get-InfrastructureRecoveryHint -Service $Service -Logs $logs
            if (-not [string]::IsNullOrWhiteSpace($logs)) {
                if (-not [string]::IsNullOrWhiteSpace($hint)) {
                    throw "Infrastructure service $Service is $health.`nRecovery hint: $hint`nRecent logs:`n$logs"
                }

                throw "Infrastructure service $Service is $health.`nRecent logs:`n$logs"
            }

            throw "Infrastructure service $Service is $health"
        }

        Start-Sleep -Seconds 2
    }

    throw "Infrastructure service $Service did not become healthy within $TimeoutSeconds seconds"
}

function Assert-InfrastructurePortsAvailable {
    param([string]$RepoRoot)

    foreach ($infraService in $infrastructureServices) {
        $containerId = Get-DockerServiceContainerId -RepoRoot $RepoRoot -Service $infraService.Name
        if (-not [string]::IsNullOrWhiteSpace($containerId)) {
            continue
        }

        $portProcess = Get-PrimaryProcessForPort -Port $infraService.Port
        if ($null -eq $portProcess) {
            continue
        }

        $path = ""
        try {
            $path = $portProcess.Path
        }
        catch {
            $path = ""
        }

        $details = "Infrastructure port $($infraService.Port) for $($infraService.Name) is already in use by PID $($portProcess.Id) ($($portProcess.ProcessName))"
        if (-not [string]::IsNullOrWhiteSpace($path)) {
            $details += " at $path"
        }

        throw "$details. Stop the conflicting process or free the port before running dev-up."
    }
}

function Resolve-MavenCommand {
    param([string]$RepoRoot)

    $candidates = @(
        $env:MAVEN_CMD,
        "mvn.cmd",
        (Join-Path $RepoRoot ".cache\maven\apache-maven-3.9.12\bin\mvn.cmd"),
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

    if (-not [string]::IsNullOrWhiteSpace($env:USERPROFILE)) {
        return (Join-Path $env:USERPROFILE ".m2\repository")
    }

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

function Get-RepoRelativePath {
    param(
        [string]$BasePath,
        [string]$Path
    )

    $baseUri = [System.Uri](([System.IO.Path]::GetFullPath($BasePath).TrimEnd('\') + '\'))
    $pathUri = [System.Uri]([System.IO.Path]::GetFullPath($Path))
    return [System.Uri]::UnescapeDataString($baseUri.MakeRelativeUri($pathUri).ToString()).Replace('/', '\')
}

function Get-TextSha256 {
    param([string]$Text)

    $bytes = [System.Text.Encoding]::UTF8.GetBytes($Text)
    $sha256 = [System.Security.Cryptography.SHA256]::Create()
    try {
        $hashBytes = $sha256.ComputeHash($bytes)
        return ([System.BitConverter]::ToString($hashBytes)).Replace("-", "")
    }
    finally {
        $sha256.Dispose()
    }
}

function Get-SharedModuleDefinitions {
    return @(
        @{ Name = "platform-kernel"; ArtifactId = "platform-kernel"; RelativePomPath = "platform-kernel\pom.xml"; SourceRoots = @("platform-kernel\src\main") },
        @{ Name = "platform-web-support"; ArtifactId = "platform-web-support"; RelativePomPath = "platform-web-support\pom.xml"; SourceRoots = @("platform-web-support\src\main") },
        @{ Name = "platform-events"; ArtifactId = "platform-events"; RelativePomPath = "platform-events\pom.xml"; SourceRoots = @("platform-events\src\main") },
        @{ Name = "auth-contract"; ArtifactId = "auth-contract"; RelativePomPath = "auth-contract\pom.xml"; SourceRoots = @("auth-contract\src\main") },
        @{ Name = "content-contract"; ArtifactId = "content-contract"; RelativePomPath = "content-contract\pom.xml"; SourceRoots = @("content-contract\src\main") },
        @{ Name = "review-contract"; ArtifactId = "review-contract"; RelativePomPath = "review-contract\pom.xml"; SourceRoots = @("review-contract\src\main") }
    )
}

function Get-SharedModulesBuildFingerprint {
    param([string]$RepoRoot)

    $paths = New-Object System.Collections.Generic.List[string]
    $paths.Add((Join-Path $RepoRoot "pom.xml"))

    foreach ($module in Get-SharedModuleDefinitions) {
        $pomPath = Join-Path $RepoRoot $module.RelativePomPath
        $paths.Add($pomPath)

        foreach ($sourceRoot in $module.SourceRoots) {
            $absoluteSourceRoot = Join-Path $RepoRoot $sourceRoot
            if (Test-Path $absoluteSourceRoot) {
                Get-ChildItem -Path $absoluteSourceRoot -Recurse -File |
                    Sort-Object FullName |
                    ForEach-Object { $paths.Add($_.FullName) }
            }
        }
    }

    $builder = New-Object System.Text.StringBuilder
    foreach ($path in $paths) {
        if (Test-Path $path) {
            $relativePath = Get-RepoRelativePath -BasePath $RepoRoot -Path $path
            $hash = (Get-FileHash -Path $path -Algorithm SHA256).Hash
            [void]$builder.AppendLine("$relativePath|$hash")
        }
        else {
            [void]$builder.AppendLine("MISSING|$path")
        }
    }

    return Get-TextSha256 -Text $builder.ToString()
}

function Get-LauncherState {
    param([string]$Path)

    if (-not (Test-Path $Path)) {
        return @{}
    }

    try {
        $raw = Get-Content -Path $Path -Raw -ErrorAction Stop
        if ([string]::IsNullOrWhiteSpace($raw)) {
            return @{}
        }

        return ($raw | ConvertFrom-Json -ErrorAction Stop)
    }
    catch {
        return @{}
    }
}

function Save-LauncherState {
    param(
        [string]$Path,
        [string]$SharedModulesBuildFingerprint
    )

    $state = [ordered]@{
        sharedModulesBuildFingerprint = $SharedModulesBuildFingerprint
        updatedAtUtc = (Get-Date).ToUniversalTime().ToString("o")
    }

    $state | ConvertTo-Json | Set-Content -Path $Path -Encoding UTF8
}

function Get-LauncherStateValue {
    param(
        $State,
        [string]$PropertyName
    )

    if ($null -eq $State) {
        return $null
    }

    if ($State -is [System.Collections.IDictionary]) {
        if ($State.Contains($PropertyName)) {
            return $State[$PropertyName]
        }

        return $null
    }

    $property = $State.PSObject.Properties[$PropertyName]
    if ($null -ne $property) {
        return $property.Value
    }

    return $null
}

function Test-MavenArtifactsInstalled {
    param([string]$RepoLocal)

    $parentPom = Join-Path $RepoLocal "com\platform\now-demo-parent\1.0.0\now-demo-parent-1.0.0.pom"
    $moduleArtifacts = @{}

    foreach ($module in Get-SharedModuleDefinitions) {
        $artifactBasePath = Join-Path $RepoLocal ("com\platform\{0}\1.0.0" -f $module.ArtifactId)
        $pomPath = Join-Path $artifactBasePath ("{0}-1.0.0.pom" -f $module.ArtifactId)
        $jarPath = Join-Path $artifactBasePath ("{0}-1.0.0.jar" -f $module.ArtifactId)
        $moduleArtifacts[$module.Name] = @{
            Pom = (Test-Path $pomPath)
            Jar = (Test-Path $jarPath)
        }
    }

    return @{
        ParentPom = (Test-Path $parentPom)
        ModuleArtifacts = $moduleArtifacts
    }
}

function Get-ServiceCurrentState {
    param([string]$Service)

    $port = $servicePorts[$Service]
    $listeningPids = @(Get-ListeningPidsForPort -Port $port)
    $listening = $listeningPids.Count -gt 0

    $healthReady = $false
    $infoReady = $false
    if ($listening) {
        $healthReady = Test-ServiceHealthEndpoint -Port $port
        $infoReady = Test-ServiceInfoEndpoint -Port $port
    }

    return @{
        Listening = $listening
        ListeningPids = $listeningPids
        ActivePid = ($listeningPids | Select-Object -First 1)
        HealthReady = $healthReady
        InfoReady = $infoReady
        Ready = ($listening -and $healthReady -and $infoReady)
    }
}

function Start-ServiceLaunch {
    param(
        [string]$Service,
        [string]$RepoRoot,
        [string]$LogRoot,
        [string]$PidRoot,
        [string]$MavenCommand,
        [string[]]$MavenCommonArguments,
        [string]$RunProfile
    )

    $pidFile = Join-Path $PidRoot "$Service.pid"
    $moduleDir = Join-Path $RepoRoot $Service
    Stop-ServiceProcesses -Service $Service -PidFile $pidFile
    Start-Sleep -Seconds 1

    $stdoutLog = Join-Path $LogRoot "$Service.out.log"
    $stderrLog = Join-Path $LogRoot "$Service.err.log"
    Reset-LogFile -Path $stdoutLog
    Reset-LogFile -Path $stderrLog

    $command = Get-ServiceCommand -MavenCommand $MavenCommand -RepoRoot $RepoRoot -Service $Service -RunProfile $RunProfile
    Write-Step "Starting $Service"

    $serviceArguments = $MavenCommonArguments + @(
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
        -WorkingDirectory $moduleDir `
        -WindowStyle Hidden `
        -RedirectStandardOutput $stdoutLog `
        -RedirectStandardError $stderrLog `
        -PassThru

    Set-Content -Path $pidFile -Value $process.Id -NoNewline

    return @{
        Process = $process
        PidFile = $pidFile
        StdoutLog = $stdoutLog
        StderrLog = $stderrLog
    }
}

function New-ServiceResult {
    param(
        [string]$Service,
        [string]$Action,
        [double]$Seconds
    )

    return [pscustomobject]@{
        Service = $Service
        Action = $Action
        Seconds = [math]::Round($Seconds, 2)
    }
}

function Invoke-ServiceStartupPass {
    param(
        [string[]]$TargetServices,
        [string]$RepoRoot,
        [string]$LogRoot,
        [string]$PidRoot,
        [string]$MavenCommand,
        [string[]]$MavenCommonArguments,
        [string]$RunProfile,
        [hashtable]$ForcedRestartReasons,
        [bool]$AllowParallelBatch,
        [System.Collections.Generic.List[object]]$ServiceResults,
        [System.Collections.Generic.List[string]]$ReusedServices,
        [System.Collections.Generic.List[string]]$RestartedServices
    )

    $batchWatch = [System.Diagnostics.Stopwatch]::StartNew()
    $pending = New-Object System.Collections.Generic.List[object]

    foreach ($service in $TargetServices) {
        $serviceWatch = [System.Diagnostics.Stopwatch]::StartNew()
        $pidFile = Join-Path $PidRoot "$service.pid"
        $forceRestart = $ForcedRestartReasons.ContainsKey($service)
        $reuseAllowed = -not $forceRestart

        if ($reuseAllowed) {
            $state = Get-ServiceCurrentState -Service $service
            if ($state.Ready) {
                Set-Content -Path $pidFile -Value $state.ActivePid -NoNewline
                Write-Step "$service is already running with PID $($state.ActivePid)"
                $serviceWatch.Stop()
                $ServiceResults.Add((New-ServiceResult -Service $service -Action "reused" -Seconds $serviceWatch.Elapsed.TotalSeconds))
                $ReusedServices.Add($service)
                continue
            }

            if ($state.Listening) {
                Write-Step "$service is listening on port $($servicePorts[$service]) but failed readiness checks and will be restarted"
            }
        }
        else {
            Write-Step "$service will be restarted: $($ForcedRestartReasons[$service])"
        }

        $launchInfo = Start-ServiceLaunch `
            -Service $service `
            -RepoRoot $RepoRoot `
            -LogRoot $LogRoot `
            -PidRoot $PidRoot `
            -MavenCommand $MavenCommand `
            -MavenCommonArguments $MavenCommonArguments `
            -RunProfile $RunProfile

        $pending.Add([pscustomobject]@{
            Service = $service
            Watch = $serviceWatch
            LaunchInfo = $launchInfo
        })

        if (-not $AllowParallelBatch) {
            $pendingItem = $pending[0]
            Wait-ForServiceReadiness `
                -Service $pendingItem.Service `
                -Port $servicePorts[$pendingItem.Service] `
                -Process $pendingItem.LaunchInfo.Process `
                -StdoutLog $pendingItem.LaunchInfo.StdoutLog `
                -StderrLog $pendingItem.LaunchInfo.StderrLog

            $activePid = Get-PrimaryListeningPidForService -Service $pendingItem.Service
            if ($null -ne $activePid) {
                Set-Content -Path $pendingItem.LaunchInfo.PidFile -Value $activePid -NoNewline
            }

            $pendingItem.Watch.Stop()
            $ServiceResults.Add((New-ServiceResult -Service $pendingItem.Service -Action "restarted" -Seconds $pendingItem.Watch.Elapsed.TotalSeconds))
            $RestartedServices.Add($pendingItem.Service)
            $pending.Clear()
        }
    }

    if ($AllowParallelBatch) {
        foreach ($pendingItem in $pending) {
            Wait-ForServiceReadiness `
                -Service $pendingItem.Service `
                -Port $servicePorts[$pendingItem.Service] `
                -Process $pendingItem.LaunchInfo.Process `
                -StdoutLog $pendingItem.LaunchInfo.StdoutLog `
                -StderrLog $pendingItem.LaunchInfo.StderrLog

            $activePid = Get-PrimaryListeningPidForService -Service $pendingItem.Service
            if ($null -ne $activePid) {
                Set-Content -Path $pendingItem.LaunchInfo.PidFile -Value $activePid -NoNewline
            }

            $pendingItem.Watch.Stop()
            $ServiceResults.Add((New-ServiceResult -Service $pendingItem.Service -Action "restarted" -Seconds $pendingItem.Watch.Elapsed.TotalSeconds))
            $RestartedServices.Add($pendingItem.Service)
        }
    }

    $batchWatch.Stop()
    return [math]::Round($batchWatch.Elapsed.TotalSeconds, 2)
}

function Get-FastModeServiceBatches {
    param([string[]]$RequestedServices)

    $batches = New-Object System.Collections.Generic.List[object]
    $groupDefinitions = @(
        @("auth-service"),
        @("content-service"),
        @("review-service", "search-service", "notification-service", "file-service"),
        @("gateway-service")
    )

    foreach ($group in $groupDefinitions) {
        $batchServices = @($group | Where-Object { $RequestedServices -contains $_ })
        if ($batchServices.Count -gt 0) {
            $batches.Add([pscustomobject]@{
                Services = $batchServices
                Parallel = ($batchServices.Count -gt 1 -and ($group -contains "review-service"))
            })
        }
    }

    $knownServices = $groupDefinitions | ForEach-Object { $_ } | Select-Object -Unique
    $extraServices = @($RequestedServices | Where-Object { $knownServices -notcontains $_ })
    if ($extraServices.Count -gt 0) {
        foreach ($service in $extraServices) {
            $batches.Add([pscustomobject]@{
                Services = @($service)
                Parallel = $false
            })
        }
    }

    return $batches
}

$repoRoot = Split-Path -Parent $PSScriptRoot
$composePath = Join-Path $repoRoot "docker-compose.yml"
$runtimeRoot = Join-Path $repoRoot ".codex-runtime"
$pidRoot = Join-Path $runtimeRoot "pids"
$logRoot = Join-Path $runtimeRoot "logs"
$statePath = Join-Path $runtimeRoot "dev-up-state.json"

New-Item -ItemType Directory -Force -Path $runtimeRoot | Out-Null
New-Item -ItemType Directory -Force -Path $pidRoot | Out-Null
New-Item -ItemType Directory -Force -Path $logRoot | Out-Null

$mavenRuntimeConfig = Ensure-MavenRuntimeConfig -RepoRoot $repoRoot -RuntimeRoot $runtimeRoot
$mavenCommand = Resolve-MavenCommand -RepoRoot $repoRoot
$mavenCommonArguments = @(Get-MavenCommonArguments `
    -SettingsPath $mavenRuntimeConfig.SettingsPath `
    -RepoLocal $mavenRuntimeConfig.RepoLocal)
$launcherState = Get-LauncherState -Path $statePath

$phaseDurations = [ordered]@{}
$serviceResults = New-Object System.Collections.Generic.List[object]
$reusedServices = New-Object System.Collections.Generic.List[string]
$restartedServices = New-Object System.Collections.Generic.List[string]
$forcedRestartReasons = @{}
$rebuiltSharedModules = $false
$sharedModulesBuildReason = ""
$fingerprintChanged = $false

Write-Step "Startup mode: $StartupMode"
Write-Step "Using Maven command: $mavenCommand"
Write-Step "Using Maven settings: $($mavenRuntimeConfig.SettingsPath)"
Write-Step "Using Maven local repository: $($mavenRuntimeConfig.RepoLocal)"

if ([System.IO.Path]::GetFileName($mavenCommand).Equals("mvnw.cmd", [System.StringComparison]::OrdinalIgnoreCase)) {
    Write-Warning "Falling back to mvnw.cmd. Prefer setting MAVEN_CMD to a real mvn.cmd path for stable Windows startup."
}

$dockerWatch = [System.Diagnostics.Stopwatch]::StartNew()
if (-not $SkipDocker) {
    if (-not (Test-Path $composePath)) {
        throw "Cannot find docker-compose.yml at $composePath"
    }

    Assert-InfrastructurePortsAvailable -RepoRoot $repoRoot

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

    foreach ($infraService in $infrastructureServices) {
        if ($StartupMode -eq "fast") {
            $containerId = Get-DockerServiceContainerId -RepoRoot $repoRoot -Service $infraService.Name
            $health = Get-DockerServiceHealth -ContainerId $containerId
            if ($health -eq "healthy" -or $health -eq "running") {
                Write-Step "Infrastructure service $($infraService.Name) is already $health"
                continue
            }
        }

        Wait-ForDockerServiceHealth -RepoRoot $repoRoot -Service $infraService.Name
    }

    if ($StartupMode -eq "fast" -and (Test-TcpPortOpen -TargetHost "127.0.0.1" -Port 8848)) {
        Write-Step "Nacos HTTP is already accepting TCP connections on 127.0.0.1:8848"
    }
    else {
        Wait-ForTcpPort -Name "Nacos HTTP" -TargetHost "127.0.0.1" -Port 8848 -TimeoutSeconds 120
    }

    if ($StartupMode -eq "fast" -and (Test-TcpPortOpen -TargetHost "127.0.0.1" -Port 9848)) {
        Write-Step "Nacos gRPC is already accepting TCP connections on 127.0.0.1:9848"
    }
    else {
        Wait-ForTcpPort -Name "Nacos gRPC" -TargetHost "127.0.0.1" -Port 9848 -TimeoutSeconds 120
    }
}
else {
    Write-Step "Skipping Docker startup"
}
$dockerWatch.Stop()
$phaseDurations["docker"] = [math]::Round($dockerWatch.Elapsed.TotalSeconds, 2)

$currentFingerprint = Get-SharedModulesBuildFingerprint -RepoRoot $repoRoot
$installedArtifacts = Test-MavenArtifactsInstalled -RepoLocal $mavenRuntimeConfig.RepoLocal
$previousFingerprint = Get-LauncherStateValue -State $launcherState -PropertyName "sharedModulesBuildFingerprint"
$fingerprintChanged = ($previousFingerprint -ne $currentFingerprint)
$sharedModules = Get-SharedModuleDefinitions
$sharedModuleNames = @($sharedModules | ForEach-Object { $_.Name })
$missingModuleNames = @(
    foreach ($module in $sharedModules) {
        $moduleState = $installedArtifacts.ModuleArtifacts[$module.Name]
        if (-not $moduleState.Pom -or -not $moduleState.Jar) {
            $module.Name
        }
    }
)

$mavenWatch = [System.Diagnostics.Stopwatch]::StartNew()
$shouldBuildSharedModules = $true

if ($StartupMode -eq "fast") {
    $shouldBuildSharedModules = $false
    if ($ForceRebuildSharedModules) {
        $shouldBuildSharedModules = $true
        $sharedModulesBuildReason = "forced by -ForceRebuildSharedModules"
    }
    elseif (-not $installedArtifacts.ParentPom) {
        $shouldBuildSharedModules = $true
        $sharedModulesBuildReason = "local Maven repository is missing now-demo-parent"
    }
    elseif ($missingModuleNames.Count -gt 0) {
        $shouldBuildSharedModules = $true
        $sharedModulesBuildReason = "local Maven repository is missing shared module artifacts: $($missingModuleNames -join ', ')"
    }
    elseif ($fingerprintChanged) {
        $shouldBuildSharedModules = $true
        $sharedModulesBuildReason = "shared module sources changed"
    }
}

if ($shouldBuildSharedModules) {
    if ($StartupMode -eq "stable") {
        Write-Step "Installing parent POM and shared modules into local Maven repository"
    }
    else {
        Write-Step "Installing parent POM and shared modules into local Maven repository ($sharedModulesBuildReason)"
    }

    Invoke-Maven -MavenCommand $mavenCommand `
        -Arguments ($mavenCommonArguments + @("-N", "install")) `
        -WorkingDirectory $repoRoot
    Invoke-Maven -MavenCommand $mavenCommand `
        -Arguments ($mavenCommonArguments + @("-pl", ($sharedModuleNames -join ","), "-am", "install", "-DskipTests")) `
        -WorkingDirectory $repoRoot

    Save-LauncherState -Path $statePath -SharedModulesBuildFingerprint $currentFingerprint
    $rebuiltSharedModules = $true
}
else {
    Write-Step "Skipping shared module install in fast mode (no changes detected)"
}
$mavenWatch.Stop()
$phaseDurations["maven"] = [math]::Round($mavenWatch.Elapsed.TotalSeconds, 2)

if ($RestartServices) {
    foreach ($service in $Services) {
        $forcedRestartReasons[$service] = "forced by -RestartServices"
    }
}
elseif ($rebuiltSharedModules -and $fingerprintChanged) {
    foreach ($service in $Services) {
        $forcedRestartReasons[$service] = "shared module sources changed"
    }
}

$servicesWatch = [System.Diagnostics.Stopwatch]::StartNew()

if ($StartupMode -eq "stable") {
    foreach ($service in $Services) {
        if (-not (Test-Path (Join-Path $repoRoot $service))) {
            throw "Cannot find module directory: $(Join-Path $repoRoot $service)"
        }

        Invoke-ServiceStartupPass `
            -TargetServices @($service) `
            -RepoRoot $repoRoot `
            -LogRoot $logRoot `
            -PidRoot $pidRoot `
            -MavenCommand $mavenCommand `
            -MavenCommonArguments $mavenCommonArguments `
            -RunProfile $RunProfile `
            -ForcedRestartReasons $forcedRestartReasons `
            -AllowParallelBatch:$false `
            -ServiceResults $serviceResults `
            -ReusedServices $reusedServices `
            -RestartedServices $restartedServices | Out-Null
    }
}
else {
    $fastBatches = Get-FastModeServiceBatches -RequestedServices $Services
    foreach ($batch in $fastBatches) {
        foreach ($service in $batch.Services) {
            if (-not (Test-Path (Join-Path $repoRoot $service))) {
                throw "Cannot find module directory: $(Join-Path $repoRoot $service)"
            }
        }

        if (($batch.Services -contains "gateway-service") -and -not $forcedRestartReasons.ContainsKey("gateway-service")) {
            $nonGatewayRestarted = @($restartedServices | Where-Object { $_ -ne "gateway-service" })
            if ($nonGatewayRestarted.Count -gt 0) {
                $forcedRestartReasons["gateway-service"] = "downstream services restarted in fast mode"
            }
        }

        $batchName = ($batch.Services -join ", ")
        if ($batch.Parallel) {
            Write-Step "Fast mode batch start: $batchName"
        }

        $batchSeconds = Invoke-ServiceStartupPass `
            -TargetServices $batch.Services `
            -RepoRoot $repoRoot `
            -LogRoot $logRoot `
            -PidRoot $pidRoot `
            -MavenCommand $mavenCommand `
            -MavenCommonArguments $mavenCommonArguments `
            -RunProfile $RunProfile `
            -ForcedRestartReasons $forcedRestartReasons `
            -AllowParallelBatch:$batch.Parallel `
            -ServiceResults $serviceResults `
            -ReusedServices $reusedServices `
            -RestartedServices $restartedServices

        if ($batch.Parallel) {
            $phaseDurations["services:$batchName"] = $batchSeconds
        }
    }
}

$servicesWatch.Stop()
$phaseDurations["services_total"] = [math]::Round($servicesWatch.Elapsed.TotalSeconds, 2)

Write-Step "Started services: $($Services -join ', ')"
Write-Host "Logs: $logRoot" -ForegroundColor Green
Write-Host "PIDs: $pidRoot" -ForegroundColor Green

Write-Host ""
Write-Host "Startup summary" -ForegroundColor Green
foreach ($phaseName in $phaseDurations.Keys) {
    Write-StageTiming -Name $phaseName -Seconds $phaseDurations[$phaseName]
}

Write-Host ""
Write-Host ("reused services: {0}" -f ($(if ($reusedServices.Count -gt 0) { $reusedServices -join ", " } else { "none" }))) -ForegroundColor Yellow
Write-Host ("restarted services: {0}" -f ($(if ($restartedServices.Count -gt 0) { $restartedServices -join ", " } else { "none" }))) -ForegroundColor Yellow
Write-Host ("rebuilt shared modules: {0}" -f ($(if ($rebuiltSharedModules) { "yes" } else { "no" }))) -ForegroundColor Yellow

if ($serviceResults.Count -gt 0) {
    Write-Host ""
    Write-Host "Per-service timing" -ForegroundColor Green
    foreach ($serviceResult in $serviceResults | Sort-Object Service) {
        Write-Host ("    {0}: {1} ({2:N2}s)" -f $serviceResult.Service, $serviceResult.Action, $serviceResult.Seconds) -ForegroundColor DarkGray
    }
}
