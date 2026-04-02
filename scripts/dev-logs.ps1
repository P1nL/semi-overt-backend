# Quick local log inspector for Windows development.
param(
    [string[]]$Services = @(),
    [int]$Tail = 200,
    [int]$ContextBefore = 2,
    [int]$ContextAfter = 12,
    [switch]$All,
    [switch]$Follow
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
[Console]::InputEncoding = [System.Text.UTF8Encoding]::new($false)
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)
$OutputEncoding = [System.Text.UTF8Encoding]::new($false)

$utf8EncodingName = "UTF8"

function Write-Section {
    param(
        [string]$Title,
        [ConsoleColor]$Color = [ConsoleColor]::Cyan
    )

    Write-Host ""
    Write-Host "==> $Title" -ForegroundColor $Color
}

function Resolve-LogFiles {
    param(
        [string]$LogRoot,
        [string[]]$TargetServices
    )

    if (-not (Test-Path $LogRoot)) {
        throw "Log directory does not exist yet: $LogRoot"
    }

    $files = New-Object System.Collections.Generic.List[System.IO.FileInfo]

    if ($TargetServices.Count -eq 0) {
        Get-ChildItem -Path $LogRoot -File |
            Where-Object { $_.Name -match '^[^.].*\.(out|err)\.log$' } |
            Sort-Object Name |
            ForEach-Object { $files.Add($_) }
        return $files
    }

    foreach ($service in $TargetServices) {
        foreach ($suffix in @("out", "err")) {
            $path = Join-Path $LogRoot "$service.$suffix.log"
            if (Test-Path $path) {
                $files.Add((Get-Item $path))
            }
        }
    }

    return $files | Sort-Object FullName -Unique
}

function Merge-Ranges {
    param([object[]]$Ranges)

    if ($null -eq $Ranges -or $Ranges.Count -eq 0) {
        return @()
    }

    $ordered = $Ranges | Sort-Object Start, End
    $merged = New-Object System.Collections.Generic.List[object]
    $current = [pscustomobject]@{
        Start = $ordered[0].Start
        End = $ordered[0].End
    }

    for ($index = 1; $index -lt $ordered.Count; $index++) {
        $candidate = $ordered[$index]
        if ($candidate.Start -le ($current.End + 1)) {
            $current.End = [Math]::Max($current.End, $candidate.End)
            continue
        }

        $merged.Add([pscustomobject]@{
            Start = $current.Start
            End = $current.End
        })
        $current = [pscustomobject]@{
            Start = $candidate.Start
            End = $candidate.End
        }
    }

    $merged.Add([pscustomobject]@{
        Start = $current.Start
        End = $current.End
    })

    return $merged
}

function Write-RecentErrorBlocks {
    param(
        [System.IO.FileInfo]$File,
        [int]$TailLines,
        [int]$Before,
        [int]$After
    )

    $lines = @(Get-Content -Path $File.FullName -Tail $TailLines -Encoding $utf8EncodingName -ErrorAction SilentlyContinue)
    if ($lines.Count -eq 0) {
        return $false
    }

    $pattern = '(?i)(\bERROR\b|\bException\b|Caused by:|\bFATAL\b|\bUnhandled\b|APPLICATION FAILED TO START|\bFailed to\b)'
    $ranges = New-Object System.Collections.Generic.List[object]

    for ($index = 0; $index -lt $lines.Count; $index++) {
        if ($lines[$index] -match $pattern) {
            $ranges.Add([pscustomobject]@{
                Start = [Math]::Max(0, $index - $Before)
                End = [Math]::Min($lines.Count - 1, $index + $After)
            })
        }
    }

    $mergedRanges = @(Merge-Ranges -Ranges $ranges)
    if ($mergedRanges.Count -eq 0) {
        return $false
    }

    Write-Section -Title $File.Name
    foreach ($range in $mergedRanges) {
        for ($index = $range.Start; $index -le $range.End; $index++) {
            Write-Host $lines[$index]
        }
        if ($range.End -lt ($lines.Count - 1)) {
            Write-Host "..."
        }
    }

    return $true
}

$repoRoot = Split-Path -Parent $PSScriptRoot
$logRoot = Join-Path $repoRoot ".codex-runtime\logs"
$logFiles = @(Resolve-LogFiles -LogRoot $logRoot -TargetServices $Services)

if ($logFiles.Count -eq 0) {
    if ($Services.Count -gt 0) {
        throw "No matching log files found under $logRoot for services: $($Services -join ', ')"
    }

    throw "No log files found under $logRoot"
}

if ($Follow -and -not $All) {
    throw "-Follow currently requires -All so the output stays readable."
}

Write-Host "Log root: $logRoot" -ForegroundColor Green

if ($All) {
    Write-Section -Title "Recent logs"
    foreach ($file in $logFiles) {
        Write-Host $file.FullName -ForegroundColor Yellow
    }

    if ($Follow) {
        Write-Host ""
        Write-Host "Following log output..." -ForegroundColor Green
        Get-Content -Path ($logFiles | ForEach-Object { $_.FullName }) -Tail $Tail -Encoding $utf8EncodingName -Wait
        exit 0
    }

    foreach ($file in $logFiles) {
        Write-Section -Title $file.Name
        $lines = @(Get-Content -Path $file.FullName -Tail $Tail -Encoding $utf8EncodingName -ErrorAction SilentlyContinue)
        if ($lines.Count -eq 0) {
            Write-Host "(empty)" -ForegroundColor DarkGray
            continue
        }

        foreach ($line in $lines) {
            Write-Host $line
        }
    }

    exit 0
}

$foundAny = $false
foreach ($file in $logFiles) {
    if (Write-RecentErrorBlocks -File $file -TailLines $Tail -Before $ContextBefore -After $ContextAfter) {
        $foundAny = $true
    }
}

if (-not $foundAny) {
    Write-Host ""
    Write-Host "No obvious error lines were found in the last $Tail lines." -ForegroundColor Yellow
    Write-Host "Use .\\scripts\\dev-logs.ps1 -All to inspect the raw tail output." -ForegroundColor Yellow
}
