param(
    [string]$OutputDir = ".\\backups",
    [string]$MySqlDumpBin = "mysqldump"
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

if ($env:DB_URL -notmatch '^jdbc:mysql://(?<host>[^:/?,]+)(:(?<port>\d+))?/(?<db>[^?]+)') {
    throw "Unsupported DB_URL format: $($env:DB_URL)"
}

$hostName = $Matches.host
$port = if ([string]::IsNullOrWhiteSpace($Matches.port)) { "3306" } else { $Matches.port }
$database = $Matches.db

New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null
$timestamp = Get-Date -Format "yyyyMMddHHmmss"
$backupFile = Join-Path $OutputDir "$database-$timestamp.sql"

$arguments = @(
    "--host=$hostName"
    "--port=$port"
    "--user=$($env:DB_USERNAME)"
    "--single-transaction"
    "--routines"
    "--events"
    $database
)

$previousPassword = $env:MYSQL_PWD
$env:MYSQL_PWD = $env:DB_PASSWORD
try {
    & $MySqlDumpBin @arguments | Out-File -FilePath $backupFile -Encoding utf8
    if ($LASTEXITCODE -ne 0) {
        throw "mysqldump failed"
    }
}
finally {
    if ($null -eq $previousPassword) {
        Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
    } else {
        $env:MYSQL_PWD = $previousPassword
    }
}

Write-Host "Database backup created: $backupFile" -ForegroundColor Green
