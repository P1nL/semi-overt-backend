#requires -Version 7.0
[CmdletBinding()]
param([string]$FrontendRoot='D:\works\semi-overt-frontend')
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
if($PSVersionTable.PSEdition -ne 'Core'){throw 'PowerShell Core required'}
$root=(Resolve-Path -LiteralPath $FrontendRoot).Path
if($root -ne 'D:\works\semi-overt-frontend'){throw 'Only the isolated demo frontend is allowed'}
$repo=Split-Path -Parent $PSScriptRoot
$runtime=Join-Path $repo '.runtime/s4'
if(Get-NetTCPConnection -State Listen -LocalPort 15173 -ErrorAction SilentlyContinue){throw 'Frontend port owned by another process; nothing stopped'}
$saved=@{}
try {
    foreach($entry in @{VITE_DEV_PROXY_TARGET='http://127.0.0.1:20080';VITE_API_BASE_URL='/api/v1'}.GetEnumerator()){
        $saved[$entry.Key]=[Environment]::GetEnvironmentVariable($entry.Key,'Process')
        [Environment]::SetEnvironmentVariable($entry.Key,$entry.Value,'Process')
    }
    $node=(Get-Command node -CommandType Application | Select-Object -First 1).Source
    $vite=Join-Path $root 'node_modules/vite/bin/vite.js'
    $process=Start-Process -FilePath $node -ArgumentList @('"'+$vite+'"','--host','127.0.0.1','--port','15173','--strictPort') -WorkingDirectory $root -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $runtime 'frontend.stdout.log') -RedirectStandardError (Join-Path $runtime 'frontend.stderr.log')
    $details=Get-CimInstance Win32_Process -Filter "ProcessId=$($process.Id)"
    @{pid=$process.Id;createdAt=$details.CreationDate.ToUniversalTime().ToString('o');root=$root;vite=$vite;proxy='http://127.0.0.1:20080'}|ConvertTo-Json|Set-Content -LiteralPath (Join-Path $runtime 'frontend-process.json') -Encoding utf8
    Write-Host 'Isolated demo frontend started on 15173; explicit API proxy 20080. PID receipt retained.'
} finally {
    foreach($name in $saved.Keys){[Environment]::SetEnvironmentVariable($name,$saved[$name],'Process')}
}
