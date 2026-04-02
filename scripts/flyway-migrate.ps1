param(
    [string]$RunProfile = "prod"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($env:DB_URL)) {
    throw "DB_URL is required"
}
if ([string]::IsNullOrWhiteSpace($env:DB_USERNAME)) {
    throw "DB_USERNAME is required"
}
if ($null -eq $env:DB_PASSWORD) {
    throw "DB_PASSWORD is required"
}

$repoRoot = Split-Path -Parent $PSScriptRoot
Push-Location $repoRoot
try {
    & .\mvnw.cmd -pl db-migration -am -DskipTests package
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to package db-migration module"
    }

    $jarPath = Get-ChildItem .\db-migration\target\*.jar |
        Where-Object { $_.Name -notlike "*.original" } |
        Select-Object -First 1 -ExpandProperty FullName

    if (-not $jarPath) {
        throw "Cannot find db-migration runnable jar"
    }

    $env:SPRING_PROFILES_ACTIVE = $RunProfile
    & java -jar $jarPath
    if ($LASTEXITCODE -ne 0) {
        throw "Flyway migration failed"
    }
    Write-Host "Flyway migration finished successfully" -ForegroundColor Green
}
finally {
    Pop-Location
}
