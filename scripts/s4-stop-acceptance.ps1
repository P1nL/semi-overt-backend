#requires -Version 7.0
[CmdletBinding()]
param([Parameter(Mandatory)][string]$RunDirectory,[switch]$StopFrontend)
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
if($PSVersionTable.PSEdition -ne 'Core'){throw 'PowerShell Core required'}
$repo=Split-Path -Parent $PSScriptRoot
$runtime=[IO.Path]::GetFullPath((Join-Path $repo '.runtime/s4'))
$run=(Resolve-Path -LiteralPath $RunDirectory).Path
if(-not $run.StartsWith($runtime+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase) -or (Split-Path -Leaf $run) -notmatch '^accept-[0-9]{8}-[0-9]{6}$'){throw 'Not an S4 acceptance run'}
$receipt=Get-Content -LiteralPath (Join-Path $run 'receipt.json') -Raw|ConvertFrom-Json
if($receipt.schema -notmatch '^s4_accept_[a-f0-9]{16}$' -or $receipt.run -ne $run){throw 'Receipt ownership mismatch'}
$stopped=@()
foreach($owned in $receipt.processes){
    $process=Get-CimInstance Win32_Process -Filter "ProcessId=$($owned.pid)"
    if(-not $process){continue}
    if($process.Name -ne 'java.exe' -or $process.CommandLine -notlike "*-Ds4.owner=$run*" -or $process.CommandLine -notlike "*$($owned.service)*"){throw 'Java ownership mismatch; not stopped'}
    Stop-Process -Id $process.ProcessId
    $stopped+=@{pid=$process.ProcessId;service=$owned.service}
}
$container=$receipt.schema.Replace('_','-')+'-redis'
$label=& docker inspect --format '{{index .Config.Labels "semi-overt.s4.owner"}}' $container 2>$null
if($LASTEXITCODE -eq 0){
    if($label -ne $receipt.schema){throw 'Redis owner mismatch'}
    & docker stop $container|Out-Null
    if($LASTEXITCODE -ne 0){throw 'Owned Redis stop failed'}
}
if($StopFrontend){
    $front=Get-Content -LiteralPath (Join-Path $runtime 'frontend-process.json') -Raw|ConvertFrom-Json
    $process=Get-CimInstance Win32_Process -Filter "ProcessId=$($front.pid)"
    if($process){
        if($process.Name -ne 'node.exe' -or $process.CommandLine -notlike "*$($front.vite)*" -or $process.CreationDate.ToUniversalTime() -ne ([datetime]$front.createdAt).ToUniversalTime()){throw 'Frontend ownership mismatch; not stopped'}
        Stop-Process -Id $process.ProcessId
        $stopped+=@{pid=$process.ProcessId;service='isolated-demo-vite'}
    }
}
@{at=(Get-Date).ToString('o');run=$run;stopped=$stopped;databaseRetained=$true;evidenceRetained=$true;productionTouched=$false}|ConvertTo-Json -Depth 5|Set-Content -LiteralPath (Join-Path $run 'cleanup-receipt.json') -Encoding utf8
Write-Host 'Only verified S4-owned processes stopped; database, uploads and evidence retained.'
