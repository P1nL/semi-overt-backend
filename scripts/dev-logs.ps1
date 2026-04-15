# Quick local log inspector for Windows development.
param(
    [string[]]$Services = @(),
    [int]$Tail = 200,
    [int]$ContextBefore = 2,
    [int]$ContextAfter = 12,
    [switch]$All,
    [switch]$Follow
)

. "$PSScriptRoot\_dev-common.ps1"

# ── Helpers ──────────────────────────────────────────────────────────────────

function Resolve-LogFiles {
    param([string]$LogRoot, [string[]]$TargetServices)
    if (-not (Test-Path $LogRoot)) { throw "Log directory does not exist: $LogRoot" }
    $files = [System.Collections.Generic.List[System.IO.FileInfo]]::new()
    if ($TargetServices.Count -eq 0) {
        Get-ChildItem -Path $LogRoot -File |
            Where-Object { $_.Name -match '^[^.].*\.(out|err)\.log$' } |
            Sort-Object Name |
            ForEach-Object { $files.Add($_) }
        return $files
    }
    foreach ($svc in $TargetServices) {
        foreach ($sfx in @("out", "err")) {
            $p = Join-Path $LogRoot "$svc.$sfx.log"
            if (Test-Path $p) { $files.Add((Get-Item $p)) }
        }
    }
    return $files | Sort-Object FullName -Unique
}

function Merge-Ranges {
    param([object[]]$Ranges)
    if ($null -eq $Ranges -or $Ranges.Count -eq 0) { return @() }
    $ordered = $Ranges | Sort-Object Start, End
    $merged  = [System.Collections.Generic.List[object]]::new()
    $cur     = [pscustomobject]@{ Start = $ordered[0].Start; End = $ordered[0].End }
    for ($i = 1; $i -lt $ordered.Count; $i++) {
        $c = $ordered[$i]
        if ($c.Start -le ($cur.End + 1)) { $cur.End = [Math]::Max($cur.End, $c.End); continue }
        $merged.Add([pscustomobject]@{ Start = $cur.Start; End = $cur.End })
        $cur = [pscustomobject]@{ Start = $c.Start; End = $c.End }
    }
    $merged.Add([pscustomobject]@{ Start = $cur.Start; End = $cur.End })
    return $merged
}

function Write-RecentErrorBlocks {
    param([System.IO.FileInfo]$File, [int]$TailLines, [int]$Before, [int]$After)
    $lines = @(Get-Content -Path $File.FullName -Tail $TailLines -Encoding UTF8 -ErrorAction SilentlyContinue)
    if ($lines.Count -eq 0) { return $false }

    $pattern = '(?i)(\bERROR\b|\bException\b|Caused by:|\bFATAL\b|\bUnhandled\b|APPLICATION FAILED TO START|\bFailed to\b)'
    $ranges  = [System.Collections.Generic.List[object]]::new()
    for ($i = 0; $i -lt $lines.Count; $i++) {
        if ($lines[$i] -match $pattern) {
            $ranges.Add([pscustomobject]@{
                Start = [Math]::Max(0, $i - $Before)
                End   = [Math]::Min($lines.Count - 1, $i + $After)
            })
        }
    }

    $merged = @(Merge-Ranges -Ranges $ranges)
    if ($merged.Count -eq 0) { return $false }

    Write-Host ""
    Write-Host "==> $($File.Name)" -ForegroundColor Cyan
    foreach ($r in $merged) {
        for ($i = $r.Start; $i -le $r.End; $i++) { Write-Host $lines[$i] }
        if ($r.End -lt ($lines.Count - 1)) { Write-Host "..." }
    }
    return $true
}

# ── Main ─────────────────────────────────────────────────────────────────────

$logRoot  = Join-Path (Split-Path -Parent $PSScriptRoot) ".codex-runtime\logs"
$logFiles = @(Resolve-LogFiles -LogRoot $logRoot -TargetServices $Services)

if ($logFiles.Count -eq 0) {
    if ($Services.Count -gt 0) { throw "No matching log files for: $($Services -join ', ')" }
    throw "No log files found under $logRoot"
}

if ($Follow -and -not $All) { throw "-Follow requires -All so output stays readable." }

Write-Host "Log root: $logRoot" -ForegroundColor Green

if ($All) {
    Write-Host ""
    Write-Host "==> Recent logs" -ForegroundColor Cyan
    foreach ($f in $logFiles) { Write-Host $f.FullName -ForegroundColor Yellow }

    if ($Follow) {
        Write-Host ""
        Write-Host "Following log output..." -ForegroundColor Green
        Get-Content -Path ($logFiles | ForEach-Object { $_.FullName }) -Tail $Tail -Encoding UTF8 -Wait
        exit 0
    }

    foreach ($f in $logFiles) {
        Write-Host ""
        Write-Host "==> $($f.Name)" -ForegroundColor Cyan
        $lines = @(Get-Content -Path $f.FullName -Tail $Tail -Encoding UTF8 -ErrorAction SilentlyContinue)
        if ($lines.Count -eq 0) { Write-Host "(empty)" -ForegroundColor DarkGray; continue }
        foreach ($l in $lines) { Write-Host $l }
    }
    exit 0
}

$foundAny = $false
foreach ($f in $logFiles) {
    if (Write-RecentErrorBlocks -File $f -TailLines $Tail -Before $ContextBefore -After $ContextAfter) {
        $foundAny = $true
    }
}

if (-not $foundAny) {
    Write-Host ""
    Write-Host "No obvious error lines were found in the last $Tail lines." -ForegroundColor Yellow
    Write-Host "Use .\scripts\dev-logs.ps1 -All to inspect the raw tail output." -ForegroundColor Yellow
}
