#requires -Version 7.0
[CmdletBinding()]
param([Parameter(Mandatory)][string]$RunDirectory,[Parameter(Mandatory)][ValidateSet('1','2','3','4')][string]$Package)
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
if($PSVersionTable.PSEdition -ne 'Core'){throw 'PowerShell Core required'}
$repo=Split-Path -Parent $PSScriptRoot
$runtime=[IO.Path]::GetFullPath((Join-Path $repo '.runtime/s4'))
$run=(Resolve-Path -LiteralPath $RunDirectory).Path
if(-not $run.StartsWith($runtime+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw 'Not a local S4 run'}
$receipt=Get-Content -LiteralPath (Join-Path $run 'receipt.json') -Raw|ConvertFrom-Json
$db=$receipt.schema;$namespace=$receipt.namespace
if($db -notmatch '^s4_accept_[a-f0-9]{16}$' -or $receipt.run -ne $run){throw 'Not an owned S4 fixture'}
foreach($owned in $receipt.processes){
    $p=Get-CimInstance Win32_Process -Filter "ProcessId=$($owned.pid)"
    if(-not $p -or $p.Name -ne 'java.exe' -or $p.CommandLine -notlike "*-Ds4.owner=$run*"){throw 'Owned service is not running'}
}
$tokens=$null;$errors=$null
$ast=[Management.Automation.Language.Parser]::ParseFile((Join-Path $PSScriptRoot 's4-acceptance.ps1'),[ref]$tokens,[ref]$errors)
if($errors.Count){throw 'Harness syntax invalid'}
# Reuse only the checked-in harness function definitions, not its startup/main body.
foreach($function in $ast.FindAll({param($node) $node -is [Management.Automation.Language.FunctionDefinitionAst]},$false)){
    . ([scriptblock]::Create($function.Extent.Text))
}
$saved=@{};$checks=[Collections.Generic.List[object]]::new();$success=$false
try {
    foreach($line in [IO.File]::ReadAllLines((Join-Path $repo '.runtime/s5/local.env'))){if($line -match '^([A-Z][A-Z0-9_]*)=(.*)$'){Set-RunEnv $Matches[1] $Matches[2]}}
    Set-RunEnv 'S4_ACCEPTANCE_DB' $db;Set-RunEnv 'S4_MYSQL_PASSWORD' $env:S5_MYSQL_ROOT_PASSWORD
    Set-RunEnv 'S4_LOGIN_PASSWORD' ([IO.File]::ReadAllText((Join-Path $run 'fixture-password')).Trim())
    $script:fixtureClasspath=$run+';'+[IO.File]::ReadAllText((Join-Path $run 'auth-classpath.txt')).Trim()
    $script:rabbitHeaders=@{Authorization='Basic '+[Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($env:RABBITMQ_USERNAME+':'+$env:RABBITMQ_PASSWORD))}
    $handler=[Net.Http.HttpClientHandler]::new();$handler.UseCookies=$false
    $script:http=[Net.Http.HttpClient]::new($handler);$script:http.Timeout=[TimeSpan]::FromSeconds(20)
    . (Join-Path $PSScriptRoot 's4-acceptance-flows.ps1')
    . (Join-Path $PSScriptRoot 's4-acceptance-files-notifications.ps1')
    $tokens=Login-Fixtures
    switch($Package){'1'{Run-PackageOne $tokens} '2'{Run-PackageTwo $tokens} '3'{Run-PackageThree $tokens} '4'{Run-PackageFour $tokens}}
    Check "Package $Package complete" $true;$success=$true
} finally {
    @{at=(Get-Date).ToString('o');success=$success;package=$Package;schema=$db;run=$run;checks=$checks;productionTouched=$false}|ConvertTo-Json -Depth 8|Set-Content -LiteralPath (Join-Path $run "package$Package-check-$(Get-Date -Format 'yyyyMMdd-HHmmss').json") -Encoding utf8
    if(Get-Variable -Name http -Scope Script -ErrorAction SilentlyContinue){$script:http.Dispose()}
    foreach($name in $saved.Keys){[Environment]::SetEnvironmentVariable($name,$saved[$name],'Process')}
}
