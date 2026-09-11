#requires -Version 7.0
[CmdletBinding()]
param([string]$BuildRoot='', [string]$MavenSettings='', [string]$Modules='')
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
if($PSVersionTable.PSEdition -ne 'Core'){throw 'PowerShell Core required'}
$repo=Split-Path -Parent $PSScriptRoot
if(-not $BuildRoot){$BuildRoot=Join-Path $repo '.runtime/s4/verify-build'}
$runtime=Join-Path $repo '.runtime/s4'
New-Item -ItemType Directory -Path $runtime -Force|Out-Null
$saved=@{}
function Set-TestEnvironment([string]$Name,[string]$Value){
    if(-not $saved.ContainsKey($Name)){$saved[$Name]=[Environment]::GetEnvironmentVariable($Name,'Process')}
    [Environment]::SetEnvironmentVariable($Name,$Value,'Process')
}
Push-Location $repo
try {
    $local=@{}
    foreach($line in [IO.File]::ReadAllLines((Join-Path $repo '.runtime/s5/local.env'))){
        if($line -match '^([A-Z][A-Z0-9_]*)=(.*)$'){$local[$Matches[1]]=$Matches[2]}
    }
    foreach($stage in @('S1','S2','S3','S4')){
        Set-TestEnvironment "${stage}_MYSQL_URL" 'jdbc:mysql://127.0.0.1:13306/'
        Set-TestEnvironment "${stage}_MYSQL_PASSWORD" $local.S5_MYSQL_ROOT_PASSWORD
        Set-TestEnvironment "${stage}_MYSQL_USERNAME" 'root'
    }
    Set-TestEnvironment 'S3_RABBIT_HOST' '127.0.0.1'
    Set-TestEnvironment 'S3_RABBIT_PORT' '15673'
    Set-TestEnvironment 'S3_RABBIT_USERNAME' $local.RABBITMQ_USERNAME
    Set-TestEnvironment 'S3_RABBIT_PASSWORD' $local.RABBITMQ_PASSWORD
    Set-TestEnvironment 'INTERNAL_TOKEN' $local.INTERNAL_TOKEN
    $arguments=@('-B','-ntp','-T','1',"-Ds3.buildRoot=$BuildRoot")
    if($MavenSettings){$arguments+=@('-s',$MavenSettings)}
    if($Modules){$arguments+=@('-pl',$Modules,'-am')}
    $arguments+='verify'
    $startedAt=Get-Date
    $log=Join-Path $runtime ('verify-'+(Get-Date -Format 'yyyyMMdd-HHmmss')+'.log')
    & mvn @arguments *> $log
    $exitCode=$LASTEXITCODE
    $suites=@(foreach($file in Get-ChildItem -LiteralPath $BuildRoot -Recurse -Filter 'TEST-*.xml' -File | Where-Object {$_.LastWriteTime -ge $startedAt}){
        $xml=[xml][IO.File]::ReadAllText($file.FullName)
        [ordered]@{name=[string]$xml.testsuite.name;tests=[int]$xml.testsuite.tests;failures=[int]$xml.testsuite.failures;errors=[int]$xml.testsuite.errors;skipped=[int]$xml.testsuite.skipped;path=$file.FullName}
    })
    $totals=@{tests=0;failures=0;errors=0;skipped=0}
    foreach($suite in $suites){foreach($field in @('tests','failures','errors','skipped')){$totals[$field]+=$suite[$field]}}
    $receipt=[ordered]@{startedAt=$startedAt.ToString('o');at=(Get-Date).ToString('o');exitCode=$exitCode;buildRoot=$BuildRoot;modules=$Modules;log=$log;totals=$totals;suites=$suites}
    $receipt|ConvertTo-Json -Depth 8|Set-Content -LiteralPath ([IO.Path]::ChangeExtension($log,'.json')) -Encoding utf8
    Get-Content -LiteralPath $log -Tail 22
    if($exitCode -ne 0){throw "S4 verify failed; see $log"}
} finally {
    foreach($name in $saved.Keys){[Environment]::SetEnvironmentVariable($name,$saved[$name],'Process')}
    Pop-Location
}
