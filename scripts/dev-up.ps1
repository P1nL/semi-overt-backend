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

. "$PSScriptRoot\_dev-common.ps1"

# ── Environment defaults ─────────────────────────────────────────────────────

if (-not $env:NACOS_SERVER_ADDR)  { $env:NACOS_SERVER_ADDR  = "127.0.0.1:8848" }
if (-not $env:NACOS_NAMESPACE)    { $env:NACOS_NAMESPACE    = "6b1b920b-c784-475e-80f8-b2fcebf24dbf" }
if (-not $env:PLATFORM_INTERNAL_TOKEN) { $env:PLATFORM_INTERNAL_TOKEN = "now-demo-local-internal-token" }
if (-not $env:DB_PASSWORD)        { $env:DB_PASSWORD        = "1234" }

# ── Helpers (dev-up only) ────────────────────────────────────────────────────

function Merge-JavaToolOptions {
    param([string]$ExistingValue)
    $required = @("-Dfile.encoding=UTF-8", "-Dsun.jnu.encoding=UTF-8")
    $result = $ExistingValue
    foreach ($opt in $required) {
        if ([string]::IsNullOrWhiteSpace($result)) { $result = $opt; continue }
        if ($result -notmatch [regex]::Escape($opt)) { $result = "$result $opt" }
    }
    return $result
}

function Invoke-HttpGet {
    param([string]$Uri)
    return Invoke-WebRequest -Uri $Uri -Method Get -TimeoutSec 5 -UseBasicParsing
}

function Test-ServiceHealthEndpoint {
    param([int]$Port)
    try {
        $r = Invoke-RestMethod -Uri "http://127.0.0.1:$Port/actuator/health" -Method Get -TimeoutSec 5
        return $r.status -eq "UP"
    }
    catch { return $false }
}

function Test-ServiceInfoEndpoint {
    param([int]$Port)
    try {
        $r = Invoke-HttpGet -Uri "http://127.0.0.1:$Port/actuator/info"
        return $r.StatusCode -ge 200 -and $r.StatusCode -lt 300
    }
    catch { return $false }
}

function Reset-LogFile {
    param([string]$Path)
    if (-not (Test-Path $Path)) { return }
    for ($i = 1; $i -le 10; $i++) {
        try   { Remove-Item $Path -Force; return }
        catch {
            try   { Move-Item $Path "$Path.$(Get-Date -Format 'yyyyMMdd-HHmmss').$i" -Force; return }
            catch {
                if ($i -eq 10) { throw "Cannot reset log file $Path — still in use." }
                Start-Sleep -Milliseconds 500
            }
        }
    }
}

function Get-RecentLogExcerpt {
    param([string]$Path, [string]$Label, [int]$Tail = 40)
    if ([string]::IsNullOrWhiteSpace($Path) -or -not (Test-Path $Path)) { return "" }
    try   { $lines = @(Get-Utf8Content -Path $Path -Tail $Tail) }
    catch { return "Recent $Label log ($Path) could not be read: $($_.Exception.Message)" }
    if ($null -eq $lines -or $lines.Count -eq 0) { return "" }
    return "Recent $Label log ($Path):`n$($lines -join "`n")"
}

# ── Docker infrastructure ────────────────────────────────────────────────────

function Get-DockerServiceContainerId {
    param([string]$Service)
    Push-Location $script:RepoRoot
    try {
        $out = docker compose ps -q $Service
        if ($null -eq $out) { return "" }
        return ($out | Out-String).Trim()
    }
    finally { Pop-Location }
}

function Get-DockerServiceHealth {
    param([string]$ContainerId)
    if ([string]::IsNullOrWhiteSpace($ContainerId)) { return "" }
    return (docker inspect --format "{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}" $ContainerId).Trim()
}

function Get-DockerServiceLogs {
    param([string]$Service, [int]$Tail = 20)
    Push-Location $script:RepoRoot
    try {
        $out = docker compose logs --tail $Tail $Service 2>&1
        if ($null -eq $out) { return "" }
        return ($out | Out-String).Trim()
    }
    finally { Pop-Location }
}

function Get-InfrastructureRecoveryHint {
    param([string]$Service, [string]$Logs)
    if ($Service -eq "redis" -and $Logs -match "Can't handle RDB format version") {
        return "Redis volume contains data from a newer version. Remove and retry: docker compose rm -sf redis; docker volume rm now-demo_redis-data"
    }
    return ""
}

function Wait-ForDockerServiceHealth {
    param([string]$Service, [int]$TimeoutSeconds = 180)
    Write-Step "Waiting for infrastructure service $Service"
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        $cid    = Get-DockerServiceContainerId -Service $Service
        $health = Get-DockerServiceHealth -ContainerId $cid
        if ($health -eq "healthy" -or $health -eq "running") { return }
        if ($health -in @("unhealthy", "exited", "dead")) {
            $logs = Get-DockerServiceLogs -Service $Service
            $hint = Get-InfrastructureRecoveryHint -Service $Service -Logs $logs
            $msg  = "Infrastructure service $Service is $health."
            if ($hint) { $msg += "`nRecovery hint: $hint" }
            if ($logs) { $msg += "`nRecent logs:`n$logs" }
            throw $msg
        }
        Start-Sleep -Seconds 2
    }
    throw "Infrastructure service $Service did not become healthy within $TimeoutSeconds seconds"
}

function Get-RequiredInfrastructure {
    # Compute the minimal set of Docker containers needed for the requested services.
    $needed = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)
    foreach ($svc in $Services) {
        if ($script:ServiceInfraDeps.ContainsKey($svc)) {
            foreach ($dep in $script:ServiceInfraDeps[$svc]) { [void]$needed.Add($dep) }
        }
        else {
            # Unknown service — assume it needs everything.
            foreach ($infra in $script:InfrastructureServices) { [void]$needed.Add($infra.Name) }
        }
    }
    return @($script:InfrastructureServices | Where-Object { $needed.Contains($_.Name) })
}

function Assert-InfrastructurePortsAvailable {
    param([object[]]$RequiredInfra)
    foreach ($svc in $RequiredInfra) {
        $cid = Get-DockerServiceContainerId -Service $svc.Name
        if (-not [string]::IsNullOrWhiteSpace($cid)) { continue }
        $proc = Get-PrimaryProcessForPort -Port $svc.Port
        if ($null -eq $proc) { continue }
        $path = try { $proc.Path } catch { "" }
        $detail = "Port $($svc.Port) for $($svc.Name) is already in use by PID $($proc.Id) ($($proc.ProcessName))"
        if ($path) { $detail += " at $path" }
        throw "$detail. Stop the conflicting process or free the port before running dev-up."
    }
}

function Start-DockerInfrastructure {
    if ($SkipDocker) {
        Write-Step "Skipping Docker startup"
        return
    }
    if (-not (Test-Path $script:ComposePath)) {
        throw "Cannot find docker-compose.yml at $($script:ComposePath)"
    }

    $requiredInfra = Get-RequiredInfrastructure
    $requiredNames = @($requiredInfra | ForEach-Object { $_.Name })
    $skippedNames  = @($script:InfrastructureServices | Where-Object { $requiredNames -notcontains $_.Name } | ForEach-Object { $_.Name })
    if ($skippedNames.Count -gt 0) {
        Write-Step "Skipping unnecessary infrastructure: $($skippedNames -join ', ')"
    }

    Assert-InfrastructurePortsAvailable -RequiredInfra $requiredInfra

    Write-Step "Starting Docker middleware: $($requiredNames -join ', ')"
    Push-Location $script:RepoRoot
    try {
        docker compose up -d @requiredNames
        if ($LASTEXITCODE -ne 0) { throw "docker compose up -d failed (exit $LASTEXITCODE)" }
    }
    finally { Pop-Location }

    # Wait for required infrastructure services — in parallel via RunspacePool.
    $pool = [RunspaceFactory]::CreateRunspacePool(1, [Math]::Max(1, $requiredInfra.Count))
    $pool.Open()
    $jobs = @()

    foreach ($svc in $requiredInfra) {
        $ps = [PowerShell]::Create().AddScript({
            param($SvcName, $StartupMode_, $RepoRoot_)

            function DockerContainerId($s) {
                Push-Location $RepoRoot_
                try { $o = docker compose ps -q $s; if ($o) { ($o | Out-String).Trim() } else { "" } }
                finally { Pop-Location }
            }
            function DockerHealth($cid) {
                if (-not $cid) { return "" }
                (docker inspect --format "{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}" $cid).Trim()
            }

            if ($StartupMode_ -eq "fast") {
                $cid = DockerContainerId $SvcName
                $h   = DockerHealth $cid
                if ($h -eq "healthy" -or $h -eq "running") { return "already:${SvcName}" }
            }

            $deadline = (Get-Date).AddSeconds(180)
            while ((Get-Date) -lt $deadline) {
                $cid = DockerContainerId $SvcName
                $h   = DockerHealth $cid
                if ($h -eq "healthy" -or $h -eq "running") { return "ready:${SvcName}" }
                if ($h -in @("unhealthy", "exited", "dead"))  { return "failed:${SvcName}:${h}" }
                Start-Sleep -Seconds 2
            }
            return "timeout:${SvcName}"
        }).AddArgument($svc.Name).AddArgument($StartupMode).AddArgument($script:RepoRoot)

        $ps.RunspacePool = $pool
        $jobs += @{ PS = $ps; Handle = $ps.BeginInvoke(); Name = $svc.Name }
    }

    foreach ($job in $jobs) {
        $result = $job.PS.EndInvoke($job.Handle)
        $job.PS.Dispose()
        $text = "$result"
        if     ($text -like "already:*") { Write-Step "Infrastructure service $($job.Name) is already healthy" }
        elseif ($text -like "ready:*")   { Write-Step "Infrastructure service $($job.Name) is ready" }
        elseif ($text -like "failed:*")  { throw "Infrastructure service $($job.Name) is unhealthy/exited" }
        elseif ($text -like "timeout:*") { throw "Infrastructure service $($job.Name) did not become healthy within 180s" }
    }
    $pool.Close()

    # Nacos TCP connectivity (HTTP + gRPC) — only if nacos is required
    if ($requiredNames -contains "nacos") {
        foreach ($portInfo in @(@{Name="Nacos HTTP"; Port=8848}, @{Name="Nacos gRPC"; Port=9848})) {
            if ($StartupMode -eq "fast" -and (Test-TcpPortOpen -TargetHost "127.0.0.1" -Port $portInfo.Port)) {
                Write-Step "$($portInfo.Name) is already accepting TCP connections on 127.0.0.1:$($portInfo.Port)"
            }
            else {
                Wait-ForTcpPort -Name $portInfo.Name -TargetHost "127.0.0.1" -Port $portInfo.Port -TimeoutSeconds 120
            }
        }
    }
}

# ── Maven ────────────────────────────────────────────────────────────────────

function Resolve-MavenCommand {
    $candidates = @(
        $env:MAVEN_CMD,
        "mvn.cmd",
        (Join-Path $script:RepoRoot ".cache\maven\apache-maven-3.9.12\bin\mvn.cmd"),
        (Join-Path $script:RepoRoot "mvnw.cmd")
    ) | Where-Object { -not [string]::IsNullOrWhiteSpace($_) }
    foreach ($c in $candidates) {
        if (Test-Path $c)     { return (Resolve-Path $c).Path }
        $cmd = Get-Command $c -ErrorAction SilentlyContinue
        if ($cmd)             { return $cmd.Source }
    }
    throw "Cannot resolve Maven command. Set MAVEN_CMD or install Maven."
}

function Resolve-PowerShellCommand {
    $cmd = Get-Command "pwsh" -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    $cmd = Get-Command "powershell.exe" -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    throw "Cannot resolve a PowerShell host."
}

function Get-DefaultMavenRepoLocal {
    if ($env:USERPROFILE) { return (Join-Path $env:USERPROFILE ".m2\repository") }
    return (Join-Path $script:RepoRoot ".cache\m2\repository")
}

function Ensure-MavenRuntimeConfig {
    $repoLocal = if ($env:MAVEN_REPO_LOCAL) {
        Resolve-AbsolutePath -Path $env:MAVEN_REPO_LOCAL -BasePath $script:RepoRoot
    } else { Get-DefaultMavenRepoLocal }
    New-Item -ItemType Directory -Force -Path $repoLocal | Out-Null

    $settingsPath = if ($env:MAVEN_SETTINGS) {
        Resolve-AbsolutePath -Path $env:MAVEN_SETTINGS -BasePath $script:RepoRoot
    } else { Join-Path $script:RuntimeRoot "maven-settings.xml" }

    if ($env:MAVEN_SETTINGS) {
        if (-not (Test-Path $settingsPath)) { throw "Cannot find Maven settings: $settingsPath" }
    }
    else {
        $escaped = [System.Security.SecurityElement]::Escape($repoLocal)
        [System.IO.File]::WriteAllText($settingsPath, @"
<settings xmlns="http://maven.apache.org/SETTINGS/1.2.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.2.0 https://maven.apache.org/xsd/settings-1.2.0.xsd">
  <localRepository>$escaped</localRepository>
</settings>
"@, $script:Utf8NoBom)
    }

    if (-not $env:MAVEN_SETTINGS)   { $env:MAVEN_SETTINGS   = $settingsPath }
    if (-not $env:MAVEN_REPO_LOCAL) { $env:MAVEN_REPO_LOCAL = $repoLocal }

    return @{ SettingsPath = $settingsPath; RepoLocal = $repoLocal }
}

function Invoke-Maven {
    param([string[]]$Arguments, [string]$WorkingDirectory = $script:RepoRoot)
    Push-Location $WorkingDirectory
    try {
        & $script:MavenCommand @Arguments
        if ($LASTEXITCODE -ne 0) { throw "Maven command failed (exit $LASTEXITCODE)" }
    }
    finally { Pop-Location }
}

function ConvertTo-SingleQuotedPowerShellLiteral {
    param([string]$Value)
    return "'" + $Value.Replace("'", "''") + "'"
}

# ── Fingerprinting (streaming SHA-256) ───────────────────────────────────────

function Get-SharedModulesBuildFingerprint {
    $sha = [System.Security.Cryptography.SHA256]::Create()
    try {
        $paths = [System.Collections.Generic.List[string]]::new()
        $paths.Add((Join-Path $script:RepoRoot "pom.xml"))
        foreach ($mod in $script:SharedModuleDefinitions) {
            $paths.Add((Join-Path $script:RepoRoot $mod.RelativePomPath))
            foreach ($sr in $mod.SourceRoots) {
                $abs = Join-Path $script:RepoRoot $sr
                if (Test-Path $abs) {
                    Get-ChildItem -Path $abs -Recurse -File | Sort-Object FullName |
                        ForEach-Object { $paths.Add($_.FullName) }
                }
            }
        }
        # Stream file bytes directly into the hash algorithm instead of
        # computing per-file hashes and concatenating strings.
        foreach ($p in $paths) {
            if (Test-Path $p) {
                $rel = Get-RepoRelativePath -BasePath $script:RepoRoot -Path $p
                $relBytes = [System.Text.Encoding]::UTF8.GetBytes("$rel|")
                $sha.TransformBlock($relBytes, 0, $relBytes.Length, $null, 0) | Out-Null
                $fileBytes = [System.IO.File]::ReadAllBytes($p)
                $sha.TransformBlock($fileBytes, 0, $fileBytes.Length, $null, 0) | Out-Null
            }
            else {
                $missingBytes = [System.Text.Encoding]::UTF8.GetBytes("MISSING|$p`n")
                $sha.TransformBlock($missingBytes, 0, $missingBytes.Length, $null, 0) | Out-Null
            }
        }
        $sha.TransformFinalBlock([byte[]]::new(0), 0, 0) | Out-Null
        return [BitConverter]::ToString($sha.Hash).Replace("-", "")
    }
    finally { $sha.Dispose() }
}

# ── Launcher state ───────────────────────────────────────────────────────────

function Get-LauncherState {
    if (-not (Test-Path $script:StatePath)) { return @{} }
    try {
        $raw = Get-Utf8Content -Path $script:StatePath -Raw
        if ([string]::IsNullOrWhiteSpace($raw)) { return @{} }
        return ($raw | ConvertFrom-Json -ErrorAction Stop)
    }
    catch { return @{} }
}

function Save-LauncherState {
    param([string]$Fingerprint)
    $state = [ordered]@{
        sharedModulesBuildFingerprint = $Fingerprint
        updatedAtUtc = (Get-Date).ToUniversalTime().ToString("o")
    }
    Set-Utf8Content -Path $script:StatePath -Value ($state | ConvertTo-Json)
}

function Get-LauncherStateValue {
    param($State, [string]$PropertyName)
    if ($null -eq $State) { return $null }
    if ($State -is [System.Collections.IDictionary]) {
        if ($State.Contains($PropertyName)) { return $State[$PropertyName] }
        return $null
    }
    $prop = $State.PSObject.Properties[$PropertyName]
    if ($null -ne $prop) { return $prop.Value }
    return $null
}

# ── Maven artifact checks ───────────────────────────────────────────────────

function Test-MavenArtifactsInstalled {
    param([string]$RepoLocal)
    $parentPom = Join-Path $RepoLocal "com\platform\now-demo-parent\1.0.0\now-demo-parent-1.0.0.pom"
    $moduleArtifacts = @{}
    foreach ($mod in $script:SharedModuleDefinitions) {
        $base = Join-Path $RepoLocal ("com\platform\{0}\1.0.0" -f $mod.ArtifactId)
        $moduleArtifacts[$mod.Name] = @{
            Pom = (Test-Path (Join-Path $base "$($mod.ArtifactId)-1.0.0.pom"))
            Jar = (Test-Path (Join-Path $base "$($mod.ArtifactId)-1.0.0.jar"))
        }
    }
    return @{ ParentPom = (Test-Path $parentPom); ModuleArtifacts = $moduleArtifacts }
}

# ── Service readiness ────────────────────────────────────────────────────────

function Get-ServiceCurrentState {
    param([string]$Service)
    $port         = $script:ServicePorts[$Service]
    $listeningPids = @(Get-ListeningPidsForPort -Port $port)
    $listening     = $listeningPids.Count -gt 0
    $healthReady   = $false; $infoReady = $false
    if ($listening) {
        $healthReady = Test-ServiceHealthEndpoint -Port $port
        $infoReady   = Test-ServiceInfoEndpoint   -Port $port
    }
    return @{
        Listening    = $listening
        ListeningPids = $listeningPids
        ActivePid    = ($listeningPids | Select-Object -First 1)
        HealthReady  = $healthReady
        InfoReady    = $infoReady
        Ready        = ($listening -and $healthReady -and $infoReady)
    }
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
    $deadline    = (Get-Date).AddSeconds($TimeoutSeconds)
    $healthReady = $false; $infoReady = $false

    while ((Get-Date) -lt $deadline) {
        $listening = (@(Get-ListeningPidsForPort -Port $Port)).Count -gt 0
        if ($listening) {
            $healthReady = Test-ServiceHealthEndpoint -Port $Port
            $infoReady   = Test-ServiceInfoEndpoint   -Port $Port
            if ($healthReady -and $infoReady) {
                Write-Step "$Service is ready on port $Port"
                return
            }
        }
        if ($Process -and -not $listening) {
            try { $Process.Refresh() } catch {}
        }
        Start-Sleep -Seconds 2
    }

    # Build failure diagnostics
    $issues = @()
    if (-not ((@(Get-ListeningPidsForPort -Port $Port)).Count -gt 0)) { $issues += "port $Port not listening" }
    if (-not $healthReady) { $issues += "/actuator/health not UP" }
    if (-not $infoReady)   { $issues += "/actuator/info unreachable" }
    if ($Process) {
        try { $Process.Refresh(); if ($Process.HasExited) { $issues += "launcher exited ($($Process.ExitCode))" } } catch {}
    }

    $msg = "$Service did not become ready within ${TimeoutSeconds}s: $($issues -join '; ')"
    $diag = @(
        (Get-RecentLogExcerpt -Path $StdoutLog -Label "$Service stdout"),
        (Get-RecentLogExcerpt -Path $StderrLog -Label "$Service stderr")
    ) | Where-Object { $_ }
    if ($diag) { $msg += ".`nRecent logs:`n$($diag -join "`n`n")" }
    throw $msg
}

# ── Service launch ───────────────────────────────────────────────────────────

function Start-ServiceLaunch {
    param([string]$Service)

    $pidFile   = Join-Path $script:PidRoot "$Service.pid"
    $moduleDir = Join-Path $script:RepoRoot $Service
    Stop-ServiceProcesses -Service $Service -PidFile $pidFile
    Start-Sleep -Seconds 1

    $stdoutLog = Join-Path $script:LogRoot "$Service.out.log"
    $stderrLog = Join-Path $script:LogRoot "$Service.err.log"
    Reset-LogFile -Path $stdoutLog
    Reset-LogFile -Path $stderrLog

    $modulePom = Join-Path $script:RepoRoot "$Service\pom.xml"
    if (-not (Test-Path $modulePom)) { throw "Cannot find module pom: $modulePom" }

    Write-Step "Starting $Service"

    $svcArgs = $script:MavenCommonArgs + @(
        "-f", $modulePom,
        "clean", "spring-boot:run",
        "-Dspring-boot.run.profiles=$RunProfile"
    )
    $encoded = ($svcArgs | ForEach-Object { ConvertTo-SingleQuotedPowerShellLiteral $_ }) -join ", "
    $javaOpts = ConvertTo-SingleQuotedPowerShellLiteral (Merge-JavaToolOptions -ExistingValue $env:JAVA_TOOL_OPTIONS)
    $mvnLiteral = ConvertTo-SingleQuotedPowerShellLiteral $script:MavenCommand

    $script_ = @(
        '$u = [System.Text.UTF8Encoding]::new($false)'
        '[Console]::InputEncoding = $u; [Console]::OutputEncoding = $u; $OutputEncoding = $u'
        "`$env:JAVA_TOOL_OPTIONS = $javaOpts"
        "& $mvnLiteral @($encoded)"
    ) -join "; "

    $proc = Start-Process -FilePath $script:PowerShellCommand `
        -ArgumentList "-NoLogo", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-Command", $script_ `
        -WorkingDirectory $moduleDir -WindowStyle Hidden `
        -RedirectStandardOutput $stdoutLog -RedirectStandardError $stderrLog `
        -PassThru

    Set-Utf8Content -Path $pidFile -Value $proc.Id -NoNewline
    return @{ Process = $proc; PidFile = $pidFile; StdoutLog = $stdoutLog; StderrLog = $stderrLog }
}

# ── Service startup orchestration ────────────────────────────────────────────

function New-ServiceResult {
    param([string]$Service, [string]$Action, [double]$Seconds)
    return [pscustomobject]@{
        Service = $Service
        Action  = $Action
        Seconds = [math]::Round($Seconds, 2)
    }
}

function Invoke-ServiceStartupPass {
    param(
        [string[]]$TargetServices,
        [bool]$AllowParallelBatch
    )

    $batchWatch = [System.Diagnostics.Stopwatch]::StartNew()
    $pending = [System.Collections.Generic.List[object]]::new()

    foreach ($svc in $TargetServices) {
        $sw = [System.Diagnostics.Stopwatch]::StartNew()
        $pidFile = Join-Path $script:PidRoot "$svc.pid"
        $forceRestart = $script:ForcedRestartReasons.ContainsKey($svc)

        if (-not $forceRestart) {
            $state = Get-ServiceCurrentState -Service $svc
            if ($state.Ready) {
                Set-Utf8Content -Path $pidFile -Value $state.ActivePid -NoNewline
                Write-Step "$svc is already running with PID $($state.ActivePid)"
                $sw.Stop()
                $script:ServiceResults.Add((New-ServiceResult -Service $svc -Action "reused" -Seconds $sw.Elapsed.TotalSeconds))
                $script:ReusedServices.Add($svc)
                continue
            }
            if ($state.Listening) {
                Write-Step "$svc is listening on port $($script:ServicePorts[$svc]) but failed readiness — restarting"
            }
        }
        else {
            Write-Step "$svc will be restarted: $($script:ForcedRestartReasons[$svc])"
        }

        $launch = Start-ServiceLaunch -Service $svc
        $pending.Add([pscustomobject]@{ Service = $svc; Watch = $sw; Launch = $launch })

        if (-not $AllowParallelBatch) {
            $item = $pending[0]
            Wait-ForServiceReadiness -Service $item.Service -Port $script:ServicePorts[$item.Service] `
                -Process $item.Launch.Process -StdoutLog $item.Launch.StdoutLog -StderrLog $item.Launch.StderrLog
            $activePid = Get-PrimaryListeningPidForService -Service $item.Service
            if ($null -ne $activePid) { Set-Utf8Content -Path $item.Launch.PidFile -Value $activePid -NoNewline }
            $item.Watch.Stop()
            $script:ServiceResults.Add((New-ServiceResult -Service $item.Service -Action "restarted" -Seconds $item.Watch.Elapsed.TotalSeconds))
            $script:RestartedServices.Add($item.Service)
            $pending.Clear()
        }
    }

    # Drain remaining parallel launches
    foreach ($item in $pending) {
        Wait-ForServiceReadiness -Service $item.Service -Port $script:ServicePorts[$item.Service] `
            -Process $item.Launch.Process -StdoutLog $item.Launch.StdoutLog -StderrLog $item.Launch.StderrLog
        $activePid = Get-PrimaryListeningPidForService -Service $item.Service
        if ($null -ne $activePid) { Set-Utf8Content -Path $item.Launch.PidFile -Value $activePid -NoNewline }
        $item.Watch.Stop()
        $script:ServiceResults.Add((New-ServiceResult -Service $item.Service -Action "restarted" -Seconds $item.Watch.Elapsed.TotalSeconds))
        $script:RestartedServices.Add($item.Service)
    }

    $batchWatch.Stop()
    return [math]::Round($batchWatch.Elapsed.TotalSeconds, 2)
}

function Get-FastModeServiceBatches {
    $groups = @(
        @("auth-service"),
        @("content-service"),
        @("review-service", "search-service", "notification-service", "file-service"),
        @("gateway-service")
    )
    $batches = [System.Collections.Generic.List[object]]::new()
    foreach ($g in $groups) {
        $matched = @($g | Where-Object { $Services -contains $_ })
        if ($matched.Count -gt 0) {
            $batches.Add([pscustomobject]@{
                Services = $matched
                Parallel = ($matched.Count -gt 1 -and $g -contains "review-service")
            })
        }
    }
    $known = $groups | ForEach-Object { $_ } | Select-Object -Unique
    foreach ($extra in @($Services | Where-Object { $known -notcontains $_ })) {
        $batches.Add([pscustomobject]@{ Services = @($extra); Parallel = $false })
    }
    return $batches
}

# ── Main ─────────────────────────────────────────────────────────────────────

$script:RepoRoot     = Split-Path -Parent $PSScriptRoot
$script:ComposePath  = Join-Path $script:RepoRoot "docker-compose.yml"
$script:RuntimeRoot  = Join-Path $script:RepoRoot ".codex-runtime"
$script:PidRoot      = Join-Path $script:RuntimeRoot "pids"
$script:LogRoot      = Join-Path $script:RuntimeRoot "logs"
$script:StatePath    = Join-Path $script:RuntimeRoot "dev-up-state.json"

New-Item -ItemType Directory -Force -Path $script:RuntimeRoot | Out-Null
New-Item -ItemType Directory -Force -Path $script:PidRoot     | Out-Null
New-Item -ItemType Directory -Force -Path $script:LogRoot     | Out-Null

$mavenRuntimeConfig        = Ensure-MavenRuntimeConfig
$script:MavenCommand       = Resolve-MavenCommand
$script:PowerShellCommand  = Resolve-PowerShellCommand
$env:JAVA_TOOL_OPTIONS     = Merge-JavaToolOptions -ExistingValue $env:JAVA_TOOL_OPTIONS
$script:MavenCommonArgs    = @("-s", $mavenRuntimeConfig.SettingsPath)
if ($env:MAVEN_REPO_LOCAL) { $script:MavenCommonArgs += "-Dmaven.repo.local=$($mavenRuntimeConfig.RepoLocal)" }

$launcherState = Get-LauncherState

$phaseDurations = [ordered]@{}
$script:ServiceResults     = [System.Collections.Generic.List[object]]::new()
$script:ReusedServices     = [System.Collections.Generic.List[string]]::new()
$script:RestartedServices  = [System.Collections.Generic.List[string]]::new()
$script:ForcedRestartReasons = @{}
$rebuiltSharedModules  = $false
$sharedModulesBuildReason = ""
$fingerprintChanged    = $false

Write-Step "Startup mode: $StartupMode"
Write-Step "Using PowerShell host: $($script:PowerShellCommand)"
Write-Step "Using JAVA_TOOL_OPTIONS: $env:JAVA_TOOL_OPTIONS"
Write-Step "Using Maven command: $($script:MavenCommand)"
Write-Step "Using Maven settings: $($mavenRuntimeConfig.SettingsPath)"
Write-Step "Using Maven local repository: $($mavenRuntimeConfig.RepoLocal)"

if ([System.IO.Path]::GetFileName($script:MavenCommand).Equals("mvnw.cmd", [System.StringComparison]::OrdinalIgnoreCase)) {
    Write-Warning "Falling back to mvnw.cmd. Prefer setting MAVEN_CMD to a real mvn.cmd path for stable Windows startup."
}

# ── Phase 1: Docker infrastructure ───────────────────────────────────────────

$dockerWatch = [System.Diagnostics.Stopwatch]::StartNew()
Start-DockerInfrastructure
$dockerWatch.Stop()
$phaseDurations["docker"] = [math]::Round($dockerWatch.Elapsed.TotalSeconds, 2)

# ── Phase 2: Shared modules ─────────────────────────────────────────────────

$currentFingerprint   = Get-SharedModulesBuildFingerprint
$installedArtifacts   = Test-MavenArtifactsInstalled -RepoLocal $mavenRuntimeConfig.RepoLocal
$previousFingerprint  = Get-LauncherStateValue -State $launcherState -PropertyName "sharedModulesBuildFingerprint"
$fingerprintChanged   = ($previousFingerprint -ne $currentFingerprint)
$sharedModuleNames    = @($script:SharedModuleDefinitions | ForEach-Object { $_.Name })
$missingModuleNames   = @(
    foreach ($mod in $script:SharedModuleDefinitions) {
        $ms = $installedArtifacts.ModuleArtifacts[$mod.Name]
        if (-not $ms.Pom -or -not $ms.Jar) { $mod.Name }
    }
)

$mavenWatch = [System.Diagnostics.Stopwatch]::StartNew()
$shouldBuild = $true

if ($StartupMode -eq "fast") {
    $shouldBuild = $false
    if     ($ForceRebuildSharedModules)       { $shouldBuild = $true; $sharedModulesBuildReason = "forced by -ForceRebuildSharedModules" }
    elseif (-not $installedArtifacts.ParentPom) { $shouldBuild = $true; $sharedModulesBuildReason = "missing now-demo-parent" }
    elseif ($missingModuleNames.Count -gt 0)  { $shouldBuild = $true; $sharedModulesBuildReason = "missing artifacts: $($missingModuleNames -join ', ')" }
    elseif ($fingerprintChanged)              { $shouldBuild = $true; $sharedModulesBuildReason = "shared module sources changed" }
}

if ($shouldBuild) {
    $label = if ($StartupMode -eq "stable") { "" } else { " ($sharedModulesBuildReason)" }
    Write-Step "Installing parent POM and shared modules$label"
    Invoke-Maven -Arguments ($script:MavenCommonArgs + @("-N", "install"))
    Invoke-Maven -Arguments ($script:MavenCommonArgs + @("-pl", ($sharedModuleNames -join ","), "-am", "clean", "install", "-DskipTests"))
    Save-LauncherState -Fingerprint $currentFingerprint
    $rebuiltSharedModules = $true
}
else {
    Write-Step "Skipping shared module install in fast mode (no changes detected)"
}
$mavenWatch.Stop()
$phaseDurations["maven"] = [math]::Round($mavenWatch.Elapsed.TotalSeconds, 2)

# ── Phase 3: Service startup ────────────────────────────────────────────────

if ($RestartServices) {
    foreach ($svc in $Services) { $script:ForcedRestartReasons[$svc] = "forced by -RestartServices" }
}
elseif ($rebuiltSharedModules -and $fingerprintChanged) {
    foreach ($svc in $Services) { $script:ForcedRestartReasons[$svc] = "shared module sources changed" }
}

$servicesWatch = [System.Diagnostics.Stopwatch]::StartNew()

if ($StartupMode -eq "stable") {
    foreach ($svc in $Services) {
        if (-not (Test-Path (Join-Path $script:RepoRoot $svc))) {
            throw "Cannot find module directory: $(Join-Path $script:RepoRoot $svc)"
        }
        Invoke-ServiceStartupPass -TargetServices @($svc) -AllowParallelBatch:$false | Out-Null
    }
}
else {
    foreach ($batch in (Get-FastModeServiceBatches)) {
        foreach ($svc in $batch.Services) {
            if (-not (Test-Path (Join-Path $script:RepoRoot $svc))) {
                throw "Cannot find module directory: $(Join-Path $script:RepoRoot $svc)"
            }
        }
        if (($batch.Services -contains "gateway-service") -and -not $script:ForcedRestartReasons.ContainsKey("gateway-service")) {
            $restarted = @($script:RestartedServices | Where-Object { $_ -ne "gateway-service" })
            if ($restarted.Count -gt 0) {
                $script:ForcedRestartReasons["gateway-service"] = "downstream services restarted in fast mode"
            }
        }
        $batchName = $batch.Services -join ", "
        if ($batch.Parallel) { Write-Step "Fast mode batch start: $batchName" }
        $batchSeconds = Invoke-ServiceStartupPass -TargetServices $batch.Services -AllowParallelBatch:$batch.Parallel
        if ($batch.Parallel) { $phaseDurations["services:$batchName"] = $batchSeconds }
    }
}

$servicesWatch.Stop()
$phaseDurations["services_total"] = [math]::Round($servicesWatch.Elapsed.TotalSeconds, 2)

# ── Summary ──────────────────────────────────────────────────────────────────

Write-Step "Started services: $($Services -join ', ')"
Write-Host "Logs: $($script:LogRoot)" -ForegroundColor Green
Write-Host "PIDs: $($script:PidRoot)" -ForegroundColor Green
Write-Host "Recent errors: .\scripts\dev-logs.ps1" -ForegroundColor Green

Write-Host ""
Write-Host "Startup summary" -ForegroundColor Green
foreach ($name in $phaseDurations.Keys) { Write-StageTiming -Name $name -Seconds $phaseDurations[$name] }

Write-Host ""
Write-Host ("reused services: {0}"   -f $(if ($script:ReusedServices.Count -gt 0) { $script:ReusedServices -join ", " } else { "none" })) -ForegroundColor Yellow
Write-Host ("restarted services: {0}" -f $(if ($script:RestartedServices.Count -gt 0) { $script:RestartedServices -join ", " } else { "none" })) -ForegroundColor Yellow
Write-Host ("rebuilt shared modules: {0}" -f $(if ($rebuiltSharedModules) { "yes" } else { "no" })) -ForegroundColor Yellow

if ($script:ServiceResults.Count -gt 0) {
    Write-Host ""
    Write-Host "Per-service timing" -ForegroundColor Green
    foreach ($r in $script:ServiceResults | Sort-Object Service) {
        Write-Host ("    {0}: {1} ({2:N2}s)" -f $r.Service, $r.Action, $r.Seconds) -ForegroundColor DarkGray
    }
}
