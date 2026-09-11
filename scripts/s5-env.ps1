#requires -Version 7.0
[CmdletBinding()]
param(
    [ValidateSet('up', 'status', 'start', 'stop', 'down', 'verify')]
    [string]$Action = 'start',
    [switch]$SkipBuild
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
if ($PSVersionTable.PSEdition -ne 'Core') { throw 'PowerShell 7 (pwsh) is required.' }
$repo = Split-Path -Parent $PSScriptRoot
$runtime = Join-Path $repo '.runtime/s5'
$envFile = Join-Path $runtime 'local.env'
$composeFile = Join-Path $repo 'docker-compose.s5.yml'
$services = [ordered]@{
    'auth-service' = 18081; 'content-service' = 18082; 'review-service' = 18083
    'search-service' = 18084; 'file-service' = 18085; 'notification-service' = 18086
    'gateway-service' = 18080
}
$savedEnvironment = @{}
function Set-LocalEnvironment([string]$Name, [AllowEmptyString()][string]$Value) {
    if (-not $savedEnvironment.ContainsKey($Name)) {
        $savedEnvironment[$Name] = [Environment]::GetEnvironmentVariable($Name, 'Process')
    }
    [Environment]::SetEnvironmentVariable($Name, $Value, 'Process')
}
function Invoke-Compose([string[]]$ComposeArgs) {
    & docker compose --project-name semi-overt-s5 --env-file $envFile -f $composeFile @ComposeArgs
    if ($LASTEXITCODE -ne 0) { throw "S5 Compose failed: $($ComposeArgs[0])" }
}
function New-LocalSecret { [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(48)) }
function Initialize-Environment {
    New-Item -ItemType Directory -Path $runtime -Force | Out-Null
    if (-not (Test-Path -LiteralPath $envFile)) {
        & docker volume inspect semi-overt-s5_mysql-data *> $null
        if ($LASTEXITCODE -eq 0) { throw 'Existing S5 data volume but missing local.env. Restore the credentials; do not regenerate them.' }
        $internalToken = New-LocalSecret
        $values = [ordered]@{
            S5_MYSQL_ROOT_PASSWORD = (New-LocalSecret)
            DB_URL = 'jdbc:mysql://127.0.0.1:13306/content_platform?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true'
            DB_USERNAME = 'semi_s5'; DB_PASSWORD = (New-LocalSecret)
            REDIS_HOST = '127.0.0.1'; REDIS_PORT = '16379'; REDIS_DB = '0'
            RABBITMQ_HOST = '127.0.0.1'; RABBITMQ_PORT = '15673'
            RABBITMQ_USERNAME = 'semi_s5'; RABBITMQ_PASSWORD = (New-LocalSecret)
            NACOS_SERVER_ADDR = '127.0.0.1:18848'; NACOS_NAMESPACE = 'semi-overt-s5'
            SPRING_CLOUD_NACOS_DISCOVERY_IP = '127.0.0.1'
            SPRING_PROFILES_ACTIVE = 's5'; SERVER_ADDRESS = '127.0.0.1'
            JWT_SIGN_KEY = (New-LocalSecret); RESET_CODE_PEPPER = (New-LocalSecret)
            INTERNAL_TOKEN = $internalToken; PLATFORM_INTERNAL_TOKEN = $internalToken
            AUTH_REFRESH_COOKIE_NAME = 'semi_overt_s5_refresh'; AUTH_REFRESH_COOKIE_SECURE = 'false'
            AUTH_ALLOWED_ORIGINS = 'http://localhost:15173,http://127.0.0.1:15173'
            CORS_ALLOWED_ORIGINS = 'http://localhost:15173,http://127.0.0.1:15173'
            FRONTEND_BASE_URL = 'http://localhost:15173'; TRUSTED_PROXIES = ''
            AUTH_SERVICE_BASE_URL = 'http://auth-service'
            STORAGE_TYPE = 'local'; STORAGE_UPLOAD_PATH = (Join-Path $runtime 'uploads').Replace('\', '/')
            STORAGE_ACCESS_PREFIX = '/static/uploads'
            # External provider setup remains a separate acceptance gate.
            MAIL_HOST = '127.0.0.1'; MAIL_PORT = '11025'; MAIL_USERNAME = 's5@localhost.invalid'
            MAIL_PASSWORD = ''; MAIL_SMTP_AUTH = 'false'; MAIL_SMTP_STARTTLS_ENABLE = 'false'
            TURNSTILE_ENABLED = 'false'; TURNSTILE_SECRET_KEY = ''; REGISTRATION_CODE_REQUIRED = 'true'
        }
        $lines = @('# Generated local credentials. Git-ignored. Do not share or commit.')
        $lines += $values.GetEnumerator() | ForEach-Object { '{0}={1}' -f $_.Key, $_.Value }
        [IO.File]::WriteAllLines($envFile, $lines, [Text.UTF8Encoding]::new($false))
        Write-Host "Created $envFile (secret values not printed)."
    }
    foreach ($line in [IO.File]::ReadAllLines($envFile)) {
        if ($line -match '^([A-Z][A-Z0-9_]*)=(.*)$') { Set-LocalEnvironment $Matches[1] $Matches[2] }
    }
    if ($env:DB_URL -notmatch '^jdbc:mysql://127\.0\.0\.1:13306/content_platform\?' -or
        $env:DB_USERNAME -ne 'semi_s5' -or $env:NACOS_SERVER_ADDR -ne '127.0.0.1:18848' -or
        $env:NACOS_NAMESPACE -ne 'semi-overt-s5' -or
        $env:REDIS_HOST -ne '127.0.0.1' -or $env:REDIS_PORT -ne '16379' -or
        $env:RABBITMQ_HOST -ne '127.0.0.1' -or $env:RABBITMQ_PORT -ne '15673' -or
        $env:SERVER_ADDRESS -ne '127.0.0.1' -or
        $env:SPRING_CLOUD_NACOS_DISCOVERY_IP -ne '127.0.0.1') { throw 'Unexpected S5 isolation settings; refusing to continue.' }
}
function Get-OwnedProcess([string]$Service) {
    $receipt = Join-Path $runtime "$Service.process.json"
    if (-not (Test-Path -LiteralPath $receipt)) { return $null }
    $state = Get-Content -LiteralPath $receipt -Raw | ConvertFrom-Json
    $process = Get-CimInstance Win32_Process -Filter "ProcessId = $($state.pid)"
    if (-not $process) { return $null }
    if ($process.Name -ne 'java.exe' -or
        $process.CommandLine -notlike "*-Ds5.owner=$runtime*" -or
        $process.CommandLine -notlike "*-Ds5.service=$Service *" -or
        $process.CreationDate.ToUniversalTime() -ne ([datetime]$state.createdAt).ToUniversalTime()) {
        throw "PID ownership mismatch for $Service; refusing to reuse or stop it."
    }
    return $process
}
function Get-ServiceHealth([int]$Port) {
    try {
        $health = Invoke-RestMethod "http://127.0.0.1:$Port/actuator/health/readiness" -TimeoutSec 5
        return $health.status -eq 'UP'
    } catch { return $false }
}
function Stop-Services {
    foreach ($service in $services.Keys) {
        $process = Get-OwnedProcess $service
        if ($process) {
            Stop-Process -Id $process.ProcessId
            Wait-Process -Id $process.ProcessId -Timeout 30 -ErrorAction SilentlyContinue
            Write-Host "Stopped owned S5 service: $service"
        }
    }
}
function Get-ServiceJar([string]$Service) {
    $jars = @(Get-ChildItem -LiteralPath (Join-Path $repo "$Service/target") -Filter '*.jar' |
        Where-Object { $_.Name -notmatch '-(sources|javadoc|tests)\.jar$' })
    if ($jars.Count -ne 1) { throw "Expected one runnable jar for $Service; run without -SkipBuild." }
    return $jars[0].FullName
}
function Start-Services {
    $running = @($services.Keys | Where-Object { $null -ne (Get-OwnedProcess $_) })
    if ($running.Count -gt 0) {
        if ($running.Count -ne $services.Count) { throw 'Partial S5 services running. Run stop, then start; migration requires no writers.' }
        foreach ($entry in $services.GetEnumerator()) {
            if (-not (Get-ServiceHealth $entry.Value)) { throw "$($entry.Key) is running but not ready. Inspect logs, then stop/start." }
        }
        Write-Host 'All seven owned S5 services are already ready. To apply code changes, stop then start.'
        return
    }
    foreach ($entry in $services.GetEnumerator()) {
        if (-not (Get-OwnedProcess $entry.Key) -and
            (Get-NetTCPConnection -State Listen -LocalPort $entry.Value -ErrorAction SilentlyContinue)) {
            throw "Port $($entry.Value) is occupied. Refusing migration/start; no other process was stopped."
        }
    }
    if (-not $SkipBuild) {
        $log = Join-Path $runtime 'build.log'
        Write-Host "Building backend modules; please wait. Progress log: $log" -ForegroundColor Cyan
        & mvn -B -ntp -T 1 -DskipTests package *> $log
        if ($LASTEXITCODE -ne 0) { throw "Build failed. Inspect $log" }
        Write-Host "Build passed. Log: $log"
    }
    if ($running.Count -eq 0) {
        Set-LocalEnvironment 'MIGRATION_MODE' 'AUTO'
        $log = Join-Path $runtime 'migration.log'
        Write-Host "Checking/migrating the isolated S5 schema. Log: $log" -ForegroundColor Cyan
        & java -Xmx256m -jar (Get-ServiceJar 'db-migration') *> $log
        if ($LASTEXITCODE -ne 0) { throw "Isolated S5 migration failed. Inspect $log; no repair/baseline performed." }
        Write-Host 'S5 schema migrated/validated successfully.'
    }
    foreach ($entry in $services.GetEnumerator()) {
        $service = $entry.Key; $port = $entry.Value
        if (-not (Get-OwnedProcess $service)) {
            if (Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue) {
                throw "Port $port occupied by an unowned process. Nothing was stopped."
            }
            Set-LocalEnvironment 'SERVER_PORT' "$port"
            $jar = Get-ServiceJar $service
            Write-Host "Starting $service on $port; waiting for readiness..." -ForegroundColor Cyan
            $arguments = @('-Xms64m', '-Xmx256m', '-XX:ActiveProcessorCount=2', '-Dfile.encoding=UTF-8',
                "`"-Ds5.owner=$runtime`"", "-Ds5.service=$service", '-jar', "`"$jar`"")
            $process = Start-Process -FilePath (Get-Command java).Source -ArgumentList $arguments `
                -WorkingDirectory $repo -WindowStyle Hidden -PassThru `
                -RedirectStandardOutput (Join-Path $runtime "$service.stdout.log") `
                -RedirectStandardError (Join-Path $runtime "$service.stderr.log")
            $details = Get-CimInstance Win32_Process -Filter "ProcessId = $($process.Id)"
            @{ pid = $process.Id; createdAt = $details.CreationDate.ToUniversalTime().ToString('o') } |
                ConvertTo-Json | Set-Content -LiteralPath (Join-Path $runtime "$service.process.json") -Encoding utf8
        }
        $deadline = (Get-Date).AddSeconds(180)
        while (-not (Get-ServiceHealth $port)) {
            if (-not (Get-OwnedProcess $service)) { throw "$service exited. Inspect logs under $runtime" }
            if ((Get-Date) -gt $deadline) { throw "$service readiness timed out. Inspect logs under $runtime" }
            Start-Sleep -Seconds 2
        }
        Write-Host "$service ready: http://127.0.0.1:$port"
    }
    Write-Host 'S5 backend ready: all seven services are UP; gateway http://127.0.0.1:18080' -ForegroundColor Green
}
function Start-Infrastructure {
    Invoke-Compose @('up', '-d', '--quiet-pull', '--wait', '--wait-timeout', '240')
    $base = 'http://127.0.0.1:18848/nacos/v1/console/namespaces'
    $namespaces = Invoke-RestMethod $base -TimeoutSec 10
    if (-not ($namespaces.data | Where-Object { $_.namespace -eq 'semi-overt-s5' })) {
        $created = Invoke-RestMethod $base -Method Post -Body @{
            customNamespaceId = 'semi-overt-s5'; namespaceName = 'semi-overt-s5'
            namespaceDesc = 'Isolated local integration; not production'
        } -TimeoutSec 10
        if ("$created" -ne 'True') { throw 'Failed to create the isolated Nacos namespace.' }
    }
    Write-Host 'Isolated middleware healthy; Nacos namespace ready.'
}
$operationLock = $null
Push-Location $repo
try {
    New-Item -ItemType Directory -Path $runtime -Force | Out-Null
    if ($Action -ne 'status') {
        try {
            $operationLock = [IO.File]::Open((Join-Path $runtime 'operation.lock'),
                [IO.FileMode]::OpenOrCreate, [IO.FileAccess]::ReadWrite, [IO.FileShare]::None)
        } catch { throw 'Another S5 operation is running. Wait for it to finish; status is still available.' }
    }
    Initialize-Environment
    switch ($Action) {
        'up' {
            Start-Infrastructure
            Write-Warning 'up only starts middleware; it does not start the seven Java services.'
            Write-Host "To start the backend: pwsh -NoProfile -File `"$PSCommandPath`" start"
        }
        'start' { Start-Infrastructure; Start-Services }
        'stop' { Stop-Services }
        'down' { Stop-Services; Invoke-Compose @('down'); Write-Host 'S5 stopped. Volumes and credentials retained.' }
        'status' {
            Invoke-Compose @('ps')
            foreach ($entry in $services.GetEnumerator()) {
                $owned = $null -ne (Get-OwnedProcess $entry.Key)
                '{0}: port={1}, owned={2}, ready={3}' -f $entry.Key, $entry.Value, $owned, ($owned -and (Get-ServiceHealth $entry.Value))
            }
        }
        'verify' {
            Start-Infrastructure
            Set-LocalEnvironment 'S1_MYSQL_URL' 'jdbc:mysql://127.0.0.1:13306/'
            Set-LocalEnvironment 'S1_MYSQL_PASSWORD' $env:S5_MYSQL_ROOT_PASSWORD
            Set-LocalEnvironment 'S2_MYSQL_URL' 'jdbc:mysql://127.0.0.1:13306/'
            Set-LocalEnvironment 'S2_MYSQL_PASSWORD' $env:S5_MYSQL_ROOT_PASSWORD
            $log = Join-Path $runtime 'verify.log'
            & mvn -B -ntp -T 1 verify *> $log
            if ($LASTEXITCODE -ne 0) { throw "Verification failed. Inspect $log" }
            Write-Host "Maven verify passed with isolated S1/S2 MySQL integration tests. Log: $log"
        }
    }
} finally {
    foreach ($name in $savedEnvironment.Keys) { [Environment]::SetEnvironmentVariable($name, $savedEnvironment[$name], 'Process') }
    if ($operationLock) { $operationLock.Dispose() }
    Pop-Location
}
