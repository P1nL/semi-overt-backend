#requires -Version 7.0
[CmdletBinding()]
param([switch]$SkipBuild,[string]$MavenSettings='',[string]$BuildRoot='', [switch]$KeepServices, [ValidateSet("all","1","2","3","4")][string]$Package="all")
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
if($PSVersionTable.PSEdition -ne 'Core'){throw 'pwsh Core required'}
$repo=Split-Path -Parent $PSScriptRoot
if(-not $BuildRoot){$BuildRoot=Join-Path $repo '.runtime/s4/build'}
$run=Join-Path $repo ('.runtime/s4/accept-'+(Get-Date -Format 'yyyyMMdd-HHmmss'))
New-Item -ItemType Directory -Path $run -Force | Out-Null
$db='s4_accept_'+[guid]::NewGuid().ToString('N').Substring(0,16)
$namespace=$db
$saved=@{};$owned=@();$checks=[Collections.Generic.List[object]]::new();$redisContainer=$null
$ports=[ordered]@{'auth-service'=20081;'content-service'=20082;'review-service'=20083;'search-service'=20084;'file-service'=20085;'notification-service'=20086;'gateway-service'=20080}
$mavenArgs=@('-B','-ntp',"-Ds3.buildRoot=$BuildRoot");if($MavenSettings){$mavenArgs+=@('-s',$MavenSettings)}
function Set-RunEnv([string]$Name,[AllowEmptyString()][string]$Value){
    if(-not $saved.ContainsKey($Name)){$saved[$Name]=[Environment]::GetEnvironmentVariable($Name,'Process')}
    [Environment]::SetEnvironmentVariable($Name,$Value,'Process')
}
function Check([string]$Name,[bool]$Pass){
    $checks.Add(@{name=$Name;pass=$Pass;at=(Get-Date).ToString('o')})
    $checks|ConvertTo-Json -Depth 6|Set-Content -LiteralPath (Join-Path $run 'checks-progress.json') -Encoding utf8
    if(-not $Pass){throw "Acceptance failed: $Name"}
    Write-Host "PASS $Name"
}
function Jar([string]$Module){
    $snapshot=Join-Path $run "jars/$Module.jar"
    if(Test-Path -LiteralPath $snapshot){return $snapshot}
    $files=@(Get-ChildItem -LiteralPath (Join-Path $BuildRoot $Module) -Filter '*.jar' | Where-Object {$_.Name -notmatch '-(sources|tests|javadoc)\.jar$'})
    if($files.Count -ne 1){throw "Expected one jar: $Module"};return $files[0].FullName
}
function DbQuery([string]$Sql){
    $output=@(& java -Xmx128m -cp $script:fixtureClasspath S4DatabaseFixture query $Sql)
    if($LASTEXITCODE -ne 0){throw 'Fixture read-only query failed'};return ,$output
}
function Start-Api([string]$Method,[string]$Path,$Body=$null,[string]$Token='', [hashtable]$ExtraHeaders=@{}){
    $request=[Net.Http.HttpRequestMessage]::new([Net.Http.HttpMethod]::new($Method),'http://127.0.0.1:20080'+$Path)
    $request.Headers.Add('Origin','http://localhost:15173')
    if($Token){$request.Headers.Authorization=[Net.Http.Headers.AuthenticationHeaderValue]::new('Bearer',$Token)}
    foreach($key in $ExtraHeaders.Keys){$request.Headers.Add($key,$ExtraHeaders[$key])}
    if($null -ne $Body){$request.Content=[Net.Http.StringContent]::new(($Body|ConvertTo-Json -Depth 12 -Compress),[Text.Encoding]::UTF8,'application/json')}
    return @{request=$request;task=$script:http.SendAsync($request);path=$Path}
}
function Finish-Api($Pending){
    $response=$Pending.task.GetAwaiter().GetResult()
    try{
        $text=$response.Content.ReadAsStringAsync().GetAwaiter().GetResult();$json=$null
        if($text){try{$json=$text|ConvertFrom-Json -Depth 20}catch{}}
        # Only synthetic fixture responses; never save login tokens or request credentials.
        $body=if($Pending.path -like '*/auth/*'){'[authentication response redacted]'}else{$json}
        @{at=(Get-Date).ToString('o');path=$Pending.path;status=[int]$response.StatusCode;response=$body}|ConvertTo-Json -Depth 20 -Compress|Add-Content -LiteralPath (Join-Path $run 'http-receipts.jsonl') -Encoding utf8
        return @{status=[int]$response.StatusCode;json=$json;path=$Pending.path}
    }finally{$response.Dispose();$Pending.request.Dispose()}
}
function Api([string]$Method,[string]$Path,$Body=$null,[string]$Token='', [hashtable]$ExtraHeaders=@{}){
    Finish-Api (Start-Api $Method $Path $Body $Token $ExtraHeaders)
}
function Wait-Until([string]$Name,[scriptblock]$Condition,[int]$Seconds=35){
    $deadline=(Get-Date).AddSeconds($Seconds)
    do{if(& $Condition){Check $Name $true;return};Start-Sleep -Milliseconds 500}while((Get-Date) -lt $deadline)
    Check $Name $false
}
function New-Draft([string]$Token){
    $r=Api POST '/api/v1/articles' $null $Token
    Check 'create draft via gateway' ($r.status -eq 200)
    if($r.json.data.PSObject.Properties.Name -contains 'articleId'){return [long]$r.json.data.articleId}
    return [long]$r.json.data.id
}
function Publish-Replay([string]$Exchange,[string]$Payload){
    $body=@{properties=@{content_type='application/json';delivery_mode=2};routing_key='';payload=$Payload;payload_encoding='string'}|ConvertTo-Json -Depth 8
    $result=Invoke-RestMethod "http://127.0.0.1:15683/api/exchanges/$namespace/$Exchange/publish" -Method Post -Headers $script:rabbitHeaders -ContentType 'application/json' -Body ([Text.Encoding]::UTF8.GetBytes($body))
    Check 'replayed event routed by real RabbitMQ' ([bool]$result.routed)
}
function Prepare-Run {
    foreach($line in [IO.File]::ReadAllLines((Join-Path $repo '.runtime/s5/local.env'))){
        if($line -match '^([A-Z][A-Z0-9_]*)=(.*)$'){Set-RunEnv $Matches[1] $Matches[2]}
    }
    if($env:NACOS_SERVER_ADDR -ne '127.0.0.1:18848' -or $env:RABBITMQ_HOST -ne '127.0.0.1' -or
            $env:RABBITMQ_PORT -ne '15673' -or $env:SERVER_ADDRESS -ne '127.0.0.1'){
        throw 'Refusing non-local infrastructure settings'
    }
    foreach($port in $ports.Values){if(Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue){throw "Acceptance port $port occupied; refusing to stop its owner"}}
    if(-not $SkipBuild){& mvn @mavenArgs -T 1 -DskipTests install *> (Join-Path $run 'build.log');if($LASTEXITCODE -ne 0){throw 'Build failed'}}
    New-Item -ItemType Directory -Path (Join-Path $run 'jars') -Force|Out-Null
    $artifacts=foreach($module in @('db-migration')+@($ports.Keys)){
        $source=Jar $module;$before=(Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash
        $destination=Join-Path $run "jars/$module.jar"
        Copy-Item -LiteralPath $source -Destination $destination
        $after=(Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash
        if($before -ne $after -or $before -ne (Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash){throw "Concurrent packaging changed $module; retry after build completes"}
        @{module=$module;sha256=$before;source=$source;snapshot=$destination}
    }
    $artifacts|ConvertTo-Json|Set-Content -LiteralPath (Join-Path $run 'artifacts.json') -Encoding utf8
    $sourceState=@{head=(& git rev-parse HEAD);frontendHead=(& git -C 'D:/works/semi-overt-frontend' rev-parse HEAD);at=(Get-Date).ToString('o')}
    $sourceState|ConvertTo-Json|Set-Content -LiteralPath (Join-Path $run 'source-state.json') -Encoding utf8
    Set-RunEnv 'S4_ACCEPTANCE_DB' $db
    Set-RunEnv 'S4_MYSQL_PASSWORD' $env:S5_MYSQL_ROOT_PASSWORD
    Set-RunEnv 'S4_LOGIN_PASSWORD' ([Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(24)))
    [IO.File]::WriteAllText((Join-Path $run 'fixture-password'),$env:S4_LOGIN_PASSWORD,[Text.UTF8Encoding]::new($false))
    $deps=Join-Path $run 'auth-classpath.txt'
    & mvn @mavenArgs -pl auth-service dependency:build-classpath "-Dmdep.outputFile=$deps" *> (Join-Path $run 'classpath.log')
    if($LASTEXITCODE -ne 0){throw 'Dependency classpath failed'}
    $script:fixtureClasspath=[IO.File]::ReadAllText($deps).Trim()
    & javac -proc:none -encoding UTF-8 -cp $script:fixtureClasspath -d $run (Join-Path $PSScriptRoot 'S4DatabaseFixture.java')
    if($LASTEXITCODE -ne 0){throw 'Acceptance fixture compile failed'}
    $script:fixtureClasspath=$run+';'+$script:fixtureClasspath
    & java -Xmx128m -cp $script:fixtureClasspath S4DatabaseFixture create
    if($LASTEXITCODE -ne 0){throw 'Isolated fixture DB creation failed'}
    Set-RunEnv 'DB_URL' "jdbc:mysql://127.0.0.1:13306/${db}?connectionTimeZone=Asia/Shanghai&forceConnectionTimeZoneToSession=true&useUnicode=true&characterEncoding=utf8&allowPublicKeyRetrieval=true&useSSL=false"
    Set-RunEnv 'DB_USERNAME' 'root';Set-RunEnv 'DB_PASSWORD' $env:S5_MYSQL_ROOT_PASSWORD;Set-RunEnv 'MIGRATION_MODE' 'AUTO'
    & java -Xmx256m -jar (Jar 'db-migration') *> (Join-Path $run 'migration.log')
    if($LASTEXITCODE -ne 0){throw 'Isolated fixture migration failed'}
    & java -Xmx128m -cp $script:fixtureClasspath S4DatabaseFixture seed
    if($LASTEXITCODE -ne 0){throw 'Isolated fixture seeding failed'}
    $script:rabbitHeaders=@{Authorization='Basic '+[Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($env:RABBITMQ_USERNAME+':'+$env:RABBITMQ_PASSWORD))}
    Invoke-RestMethod "http://127.0.0.1:15683/api/vhosts/$namespace" -Method Put -Headers $script:rabbitHeaders -ContentType 'application/json' -Body '{}'|Out-Null
    Invoke-RestMethod "http://127.0.0.1:15683/api/permissions/$namespace/$($env:RABBITMQ_USERNAME)" -Method Put -Headers $script:rabbitHeaders -ContentType 'application/json' -Body '{"configure":".*","write":".*","read":".*"}'|Out-Null
    Invoke-RestMethod 'http://127.0.0.1:18848/nacos/v1/console/namespaces' -Method Post -Body @{customNamespaceId=$namespace;namespaceName=$namespace;namespaceDesc='S4 isolated acceptance'}|Out-Null
    Set-RunEnv 'NACOS_NAMESPACE' $namespace;Set-RunEnv 'SPRING_RABBITMQ_VIRTUAL_HOST' $namespace
    Set-RunEnv 'NACOS_SERVER_ADDR' '127.0.0.1:18848';Set-RunEnv 'SPRING_CLOUD_NACOS_DISCOVERY_IP' '127.0.0.1'
    Set-RunEnv 'SERVER_ADDRESS' '127.0.0.1';Set-RunEnv 'RABBITMQ_HOST' '127.0.0.1';Set-RunEnv 'RABBITMQ_PORT' '15673'
    $script:redisContainer=$db.Replace('_','-')+'-redis'
    & docker run -d --name $script:redisContainer --label "semi-overt.s4.owner=$db" -p '127.0.0.1::6379' redis:7.2.7 *> (Join-Path $run 'redis-start.log')
    if($LASTEXITCODE -ne 0){throw 'Isolated acceptance Redis startup failed'}
    $redisPort=& docker inspect --format '{{(index (index .NetworkSettings.Ports "6379/tcp") 0).HostPort}}' $script:redisContainer
    Set-RunEnv 'REDIS_HOST' '127.0.0.1';Set-RunEnv 'REDIS_PORT' $redisPort.Trim();Set-RunEnv 'REDIS_DB' '0'
    Set-RunEnv 'STORAGE_UPLOAD_PATH' ((Join-Path $run 'uploads').Replace('\','/'))
    Set-RunEnv 'STORAGE_TYPE' 'local';Set-RunEnv 'STORAGE_ACCESS_PREFIX' '/static/uploads'
    Set-RunEnv 'PLATFORM_SEARCH_FULLTEXT_ENABLED' 'true'
    Set-RunEnv 'SEARCH_FULLTEXT_ENABLED' 'true'
    Set-RunEnv 'AUTH_SERVICE_BASE_URL' 'http://auth-service'
    Set-RunEnv 'AUTH_REFRESH_COOKIE_SECURE' 'false'
    Set-RunEnv 'AUTH_REFRESH_COOKIE_NAME' ($db+'_refresh');Set-RunEnv 'SPRING_PROFILES_ACTIVE' 's4accept'
    Set-RunEnv 'EVENT_OUTBOX_PUBLISH_DELAY_MS' '500';Set-RunEnv 'EVENT_RETRY_DELAY_MS' '500'
}
function Start-Services {
    foreach($entry in $ports.GetEnumerator()){
        Start-OneService $entry.Key $entry.Value
    }
    $handler=[Net.Http.HttpClientHandler]::new();$handler.UseCookies=$false
    $script:http=[Net.Http.HttpClient]::new($handler);$script:http.Timeout=[TimeSpan]::FromSeconds(15)
}
function Start-OneService([string]$Service,[int]$Port) {
        $entry=@{Key=$Service;Value=$Port}
        Set-RunEnv 'SERVER_PORT' "$($entry.Value)"
        $arguments=@('-Xms64m','-Xmx256m','-XX:ActiveProcessorCount=2','-Duser.timezone=Asia/Shanghai',"`"-Ds4.owner=$run`"",'-jar',"`"$(Jar $entry.Key)`"")
        $p=Start-Process -FilePath (Get-Command java).Source -ArgumentList $arguments -WorkingDirectory $repo -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $run "$($entry.Key).out.log") -RedirectStandardError (Join-Path $run "$($entry.Key).err.log")
        $script:owned+=@{pid=$p.Id;service=$entry.Key;port=$entry.Value}
        $deadline=(Get-Date).AddSeconds(150)
        do{
            $alive=Get-Process -Id $p.Id -ErrorAction SilentlyContinue;if(-not $alive){throw "$($entry.Key) exited"}
            try{$health=Invoke-RestMethod "http://127.0.0.1:$($entry.Value)/actuator/health/readiness" -TimeoutSec 3;if($health.status -eq 'UP'){break}}catch{}
            Start-Sleep -Seconds 1
        }while((Get-Date) -lt $deadline)
        if((Get-Date) -ge $deadline){throw "$($entry.Key) readiness timed out"}
        Check "$($entry.Key) readiness" $true
}
function Stop-OwnedServices {
    foreach($item in $owned){
        $p=Get-CimInstance Win32_Process -Filter "ProcessId=$($item.pid)"
        if($p -and $p.Name -eq 'java.exe' -and $p.CommandLine -like "*-Ds4.owner=$run*" -and $p.CommandLine -like "*$($item.service)*") {
            Stop-Process -Id $p.ProcessId
        }
    }
}

. (Join-Path $PSScriptRoot 's4-acceptance-flows.ps1')
. (Join-Path $PSScriptRoot 's4-acceptance-files-notifications.ps1')
Push-Location $repo
$success=$false
try {
    Prepare-Run;Start-Services;$tokens=Login-Fixtures
    foreach($number in @(1,2,3,4)) {
        if($Package -ne 'all' -and $Package -ne "$number"){continue}
        switch($number) {1{Run-PackageOne $tokens} 2{Run-PackageTwo $tokens} 3{Run-PackageThree $tokens} 4{Run-PackageFour $tokens}}
        Check "Package $number complete" $true
    }
    $success=$true
} finally {
    @{at=(Get-Date).ToString('o');success=$success;package=$Package;schema=$db;namespace=$namespace;run=$run;checks=$checks;processes=$owned;productionTouched=$false}|ConvertTo-Json -Depth 8|Set-Content -LiteralPath (Join-Path $run 'receipt.json') -Encoding utf8
    if(-not ($KeepServices -and $success)){Stop-OwnedServices}
    if($redisContainer -and -not ($KeepServices -and $success)){
        $label=& docker inspect --format '{{index .Config.Labels "semi-overt.s4.owner"}}' $redisContainer 2>$null
        if($LASTEXITCODE -eq 0 -and $label -eq $db){& docker stop $redisContainer *> (Join-Path $run 'redis-stop.log')}
    }
    if(Get-Variable -Name http -Scope Script -ErrorAction SilentlyContinue){$script:http.Dispose()}
    foreach($name in $saved.Keys){[Environment]::SetEnvironmentVariable($name,$saved[$name],'Process')}
    Pop-Location
    Write-Host "S4 receipts retained: $run (credentials not printed)"
}
