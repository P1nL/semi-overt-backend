#requires -Version 7.0
[CmdletBinding()]
param([Parameter(Mandatory)][string]$RunDirectory)
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
$repo=Split-Path -Parent $PSScriptRoot;$root=[IO.Path]::GetFullPath((Join-Path $repo '.runtime/s5-recovery'))
$run=(Resolve-Path -LiteralPath $RunDirectory).Path
if(-not $run.StartsWith($root+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase) -or (Split-Path -Leaf $run) -notmatch '^run-\d{8}-\d{6}$'){throw 'Not a recovery run'}
$r=Get-Content (Join-Path $run 'receipt.json') -Raw|ConvertFrom-Json
if($r.run -ne $run -or $r.scope -ne 'S5_RECOVERY_ONLY' -or $r.schema -notmatch '^s4_accept_[a-f0-9]{16}$'){throw 'Invalid receipt'}
$stopped=@()
foreach($o in $r.processes){
    $p=Get-CimInstance Win32_Process -Filter "ProcessId=$($o.pid)"
    if(-not $p){continue}
    if($p.Name -ne 'java.exe' -or $p.CommandLine -notlike "*-Ds5r.owner=$run*" -or $p.CreationDate.ToUniversalTime() -ne ([datetime]$o.createdAt).ToUniversalTime()){throw 'Java ownership mismatch'}
    Stop-Process -Id $p.ProcessId;$stopped+=@{pid=$p.ProcessId;instance=$o.instance}
}
if($r.proxy){$p=Get-CimInstance Win32_Process -Filter "ProcessId=$($r.proxy.pid)";if($p){if($p.Name -ne 'node.exe' -or $p.CommandLine -notlike "*$run*" -or $p.CommandLine -notlike '*s5-fault-proxy.mjs*'){throw 'Proxy ownership mismatch'};Stop-Process -Id $p.ProcessId;$stopped+=@{pid=$p.ProcessId;instance='fault-proxy'}}}
$frontend=Join-Path $run 'frontend-process.json'
if(Test-Path $frontend){$f=Get-Content $frontend -Raw|ConvertFrom-Json;$p=Get-CimInstance Win32_Process -Filter "ProcessId=$($f.pid)";if($p){if($p.Name -ne 'node.exe' -or $p.CommandLine -notlike "*$($f.vite)*" -or $p.CreationDate.ToUniversalTime() -ne ([datetime]$f.createdAt).ToUniversalTime()){throw 'Vite ownership mismatch'};Stop-Process -Id $p.ProcessId;$stopped+=@{pid=$p.ProcessId;instance='demo-vite'}}}
$redis=$r.schema.Replace('_','-')+'-redis';$label=& docker inspect --format '{{index .Config.Labels "semi-overt.s4.owner"}}' $redis 2>$null
if($LASTEXITCODE -eq 0){if($label -ne $r.schema){throw 'Redis ownership mismatch'};& docker stop $redis|Out-Null}
@{at=(Get-Date).ToString('o');stopped=$stopped;databaseAndEvidenceRetained=$true;sharedInfrastructureStopped=$false}|ConvertTo-Json -Depth 5|Set-Content (Join-Path $run 'cleanup.json')
Write-Host 'Stopped only recovery-owned processes; retained database/evidence and shared infrastructure.'
