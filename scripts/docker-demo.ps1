[CmdletBinding()]
param(
    [Parameter(Position = 0)]
    [ValidateSet('init', 'build', 'pull', 'up', 'down', 'status', 'logs', 'export', 'import')]
    [string]$Action = 'status',

    [string]$FrontendPath,
    [string]$OutputPath,
    [string]$PackagePath,
    [string]$Service
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

if ($PSVersionTable.PSEdition -ne 'Core' -or $PSVersionTable.PSVersion.Major -lt 7) {
    throw 'This script requires PowerShell 7+. Run it with pwsh.'
}

function New-RandomBase64([int]$ByteCount = 48) {
    $bytes = [byte[]]::new($ByteCount)
    [System.Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
    [Convert]::ToBase64String($bytes)
}

function Read-DotEnv([string]$Path) {
    $values = @{}
    if (-not (Test-Path -LiteralPath $Path)) { return $values }
    foreach ($line in Get-Content -LiteralPath $Path) {
        $trimmed = $line.Trim()
        if (-not $trimmed -or $trimmed.StartsWith('#')) { continue }
        $pair = $trimmed.Split('=', 2)
        if ($pair.Count -eq 2) { $values[$pair[0].Trim()] = $pair[1].Trim() }
    }
    return $values
}

$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$repoCompose = Join-Path $repoRoot 'deploy/docker/compose.yml'
if (Test-Path -LiteralPath $repoCompose) {
    $basePath = $repoRoot
    $composePath = $repoCompose
    $envPath = Join-Path $repoRoot '.runtime/docker-demo.env'
    $isSourceCheckout = $true
} else {
    $basePath = $PSScriptRoot
    $composePath = Join-Path $PSScriptRoot 'compose.yml'
    $envPath = Join-Path $PSScriptRoot '.runtime/docker-demo.env'
    $isSourceCheckout = $false
}

if (-not (Test-Path -LiteralPath $composePath)) {
    throw "Compose file not found: $composePath"
}

function Initialize-Environment {
    if (Test-Path -LiteralPath $envPath) {
        $settings = Read-DotEnv $envPath
        if (-not $settings.ContainsKey('RESET_CODE_PEPPER') -or [string]::IsNullOrWhiteSpace($settings['RESET_CODE_PEPPER'])) {
            $replacement = "RESET_CODE_PEPPER=$(New-RandomBase64 48)"
            $pattern = '^\s*RESET_CODE_PEPPER='
            $found = $false
            $lines = foreach ($line in Get-Content -LiteralPath $envPath) {
                if ($line -match $pattern) {
                    $found = $true
                    $replacement
                } else {
                    $line
                }
            }
            if (-not $found) { $lines = @($lines) + $replacement }
            $lines | Set-Content -LiteralPath $envPath -Encoding utf8NoBOM
            Write-Host "Added missing RESET_CODE_PEPPER to: $envPath"
        } else {
            Write-Host "Environment already exists: $envPath"
        }
        return
    }
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $envPath) | Out-Null
    @(
        'BACKEND_IMAGE=ghcr.io/p1nl/semi-overt-backend:demo'
        'FRONTEND_IMAGE=ghcr.io/p1nl/semi-overt-frontend:demo'
        'APP_PORT=18000'
        'GATEWAY_PORT=18080'
        'MAILPIT_PORT=18025'
        'ADMINER_PORT=18026'
        'RABBITMQ_USERNAME=semi_overt'
        "MYSQL_ROOT_PASSWORD=$(New-RandomBase64 36)"
        "DB_PASSWORD=$(New-RandomBase64 36)"
        "RABBITMQ_PASSWORD=$(New-RandomBase64 36)"
        "JWT_SIGN_KEY=$(New-RandomBase64 48)"
        "INTERNAL_TOKEN=$(New-RandomBase64 48)"
        "RESET_CODE_PEPPER=$(New-RandomBase64 48)"
    ) | Set-Content -LiteralPath $envPath -Encoding utf8NoBOM
    Write-Host "Created private runtime environment: $envPath"
}

function Assert-Docker {
    docker info *> $null
    if ($LASTEXITCODE -ne 0) { throw 'Docker Engine is not available.' }
    docker compose version *> $null
    if ($LASTEXITCODE -ne 0) { throw 'Docker Compose is not available.' }
}

function Invoke-Compose([string[]]$Arguments) {
    Initialize-Environment
    & docker compose --project-name semi-overt-demo --env-file $envPath --file $composePath @Arguments
    if ($LASTEXITCODE -ne 0) { throw "docker compose failed with exit code $LASTEXITCODE" }
}

Assert-Docker

switch ($Action) {
    'init' {
        Initialize-Environment
    }
    'build' {
        if (-not $isSourceCheckout) { throw 'Build is only available from the source checkout.' }
        Initialize-Environment
        $settings = Read-DotEnv $envPath
        $backendImage = $settings['BACKEND_IMAGE']
        $frontendImage = $settings['FRONTEND_IMAGE']
        if (-not $FrontendPath) {
            $FrontendPath = [IO.Path]::GetFullPath((Join-Path $repoRoot '../semi-overt-frontend'))
        }
        if (-not (Test-Path -LiteralPath (Join-Path $FrontendPath 'Dockerfile'))) {
            throw "Frontend Dockerfile not found under: $FrontendPath"
        }
        & docker build --file (Join-Path $repoRoot 'Dockerfile') --tag $backendImage $repoRoot
        if ($LASTEXITCODE -ne 0) { throw 'Backend image build failed.' }
        & docker build --file (Join-Path $FrontendPath 'Dockerfile') --tag $frontendImage $FrontendPath
        if ($LASTEXITCODE -ne 0) { throw 'Frontend image build failed.' }
        Write-Host "Built $backendImage and $frontendImage"
    }
    'pull' {
        Invoke-Compose @('pull')
    }
    'up' {
        Invoke-Compose @('up', '-d', '--remove-orphans', '--wait', '--wait-timeout', '420')
        $settings = Read-DotEnv $envPath
        Write-Host "Application: http://localhost:$($settings['APP_PORT'])"
        Write-Host "Gateway:     http://127.0.0.1:$($settings['GATEWAY_PORT'])"
        Write-Host "Mailpit:     http://127.0.0.1:$($settings['MAILPIT_PORT'])"
    }
    'down' {
        Invoke-Compose @('down', '--remove-orphans')
    }
    'status' {
        Invoke-Compose @('ps')
    }
    'logs' {
        $args = @('logs', '--tail', '200')
        if ($Service) { $args += $Service }
        Invoke-Compose $args
    }
    'export' {
        if (-not $OutputPath) { throw 'export requires -OutputPath.' }
        Initialize-Environment
        $resolvedOutput = [IO.Path]::GetFullPath($OutputPath)
        if (Test-Path -LiteralPath $resolvedOutput) {
            if ((Get-ChildItem -LiteralPath $resolvedOutput -Force | Select-Object -First 1)) {
                throw "Output directory must be empty: $resolvedOutput"
            }
        } else {
            New-Item -ItemType Directory -Force -Path $resolvedOutput | Out-Null
        }
        $settings = Read-DotEnv $envPath
        $images = @(
            $settings['BACKEND_IMAGE'],
            $settings['FRONTEND_IMAGE'],
            'mysql:8.0.46',
            'redis:7.2.7',
            'rabbitmq:3.13.7-management',
            'nacos/nacos-server:v2.3.2',
            'axllent/mailpit:v1.27.4'
        )
        foreach ($image in $images) {
            & docker image inspect $image *> $null
            if ($LASTEXITCODE -ne 0) {
                & docker pull $image
                if ($LASTEXITCODE -ne 0) { throw "Unable to obtain image: $image" }
            }
        }
        $archive = Join-Path $resolvedOutput 'images.tar'
        & docker save --output $archive @images
        if ($LASTEXITCODE -ne 0) { throw 'docker save failed.' }
        Copy-Item -LiteralPath $composePath -Destination (Join-Path $resolvedOutput 'compose.yml')
        Copy-Item -LiteralPath $PSCommandPath -Destination (Join-Path $resolvedOutput 'docker-demo.ps1')
        if ($isSourceCheckout) {
            Copy-Item -LiteralPath (Join-Path $repoRoot 'deploy/docker/.env.example') -Destination (Join-Path $resolvedOutput '.env.example')
            Copy-Item -LiteralPath (Join-Path $repoRoot 'deploy/docker/README.md') -Destination (Join-Path $resolvedOutput 'README.md')
        }
        Write-Host "Offline package created: $resolvedOutput"
    }
    'import' {
        if (-not $PackagePath) { $PackagePath = $basePath }
        $archive = Join-Path ([IO.Path]::GetFullPath($PackagePath)) 'images.tar'
        if (-not (Test-Path -LiteralPath $archive)) { throw "Image archive not found: $archive" }
        & docker load --input $archive
        if ($LASTEXITCODE -ne 0) { throw 'docker load failed.' }
        Write-Host 'Images imported. Run init, then up.'
    }
}
