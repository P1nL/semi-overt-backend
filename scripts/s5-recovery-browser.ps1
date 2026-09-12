#requires -Version 7.0
[CmdletBinding()]
param([Parameter(Mandatory)][string]$RunDirectory)
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
$repo=Split-Path -Parent $PSScriptRoot
$root=[IO.Path]::GetFullPath((Join-Path $repo '.runtime/s5-recovery'))
$run=(Resolve-Path -LiteralPath $RunDirectory).Path
if(-not $run.StartsWith($root+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw 'Not a recovery run'}
$receipt=Get-Content -LiteralPath (Join-Path $run 'receipt.json') -Raw|ConvertFrom-Json
if(-not $receipt.success -or $receipt.scope -ne 'S5_RECOVERY_ONLY'){throw 'Passed backend recovery required first'}
$gateway=@($receipt.processes|Where-Object {$_.instance -eq 'gateway-service-a'})[-1]
$process=Get-CimInstance Win32_Process -Filter "ProcessId=$($gateway.pid)"
if(-not $process -or $process.Name -ne 'java.exe' -or $process.CommandLine -notlike "*-Ds5r.owner=$run*" -or $process.CreationDate.ToUniversalTime() -ne ([datetime]$gateway.createdAt).ToUniversalTime()){throw 'The passed recovery gateway is no longer running'}
if(Get-NetTCPConnection -State Listen -LocalPort 15173 -ErrorAction SilentlyContinue){throw '15173 already owned; nothing stopped'}
$frontend='D:\works\semi-overt-frontend';$vite=Join-Path $frontend 'node_modules/vite/bin/vite.js'
$saved=@{}
try{
    foreach($entry in @{VITE_API_BASE_URL='/api/v1';VITE_DEV_PROXY_TARGET='http://127.0.0.1:21080'}.GetEnumerator()){$saved[$entry.Key]=[Environment]::GetEnvironmentVariable($entry.Key,'Process');[Environment]::SetEnvironmentVariable($entry.Key,$entry.Value,'Process')}
    $p=Start-Process -FilePath (Get-Command node -CommandType Application|Select-Object -First 1).Source -ArgumentList @("`"$vite`"",'--host','127.0.0.1','--port','15173','--strictPort') -WorkingDirectory $frontend -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $run 'frontend.out.log') -RedirectStandardError (Join-Path $run 'frontend.err.log')
    $proc=Get-CimInstance Win32_Process -Filter "ProcessId=$($p.Id)"
    @{pid=$p.Id;vite=$vite;createdAt=$proc.CreationDate.ToUniversalTime().ToString('o')}|ConvertTo-Json|Set-Content (Join-Path $run 'frontend-process.json')
}finally{foreach($name in $saved.Keys){[Environment]::SetEnvironmentVariable($name,$saved[$name],'Process')}}
