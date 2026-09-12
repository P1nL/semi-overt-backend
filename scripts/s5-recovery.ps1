#requires -Version 7.0
[CmdletBinding()]
param([switch]$SkipBuild,[string]$BuildRoot='', [switch]$KeepServices)
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
if($PSVersionTable.PSEdition -ne 'Core'){throw 'pwsh Core required'}
$repo=Split-Path -Parent $PSScriptRoot
$script:suiteScripts=$PSScriptRoot
# Pin the published port: Docker assigns a different ephemeral port on start
# when HostPort was empty, which is not a recoverable outage at the same address.
$reservation=[Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback,0)
$reservation.Start();$script:redisPort=$reservation.LocalEndpoint.Port;$reservation.Stop()
if(-not $BuildRoot){$BuildRoot=Join-Path $repo '.runtime/s5-recovery/build'}
$run=Join-Path $repo ('.runtime/s5-recovery/run-'+(Get-Date -Format 'yyyyMMdd-HHmmss'))
New-Item -ItemType Directory -Force -Path $run|Out-Null
$db='s4_accept_'+[guid]::NewGuid().ToString('N').Substring(0,16);$namespace=$db
$saved=@{};$owned=@();$checks=[Collections.Generic.List[object]]::new();$redisContainer=$null;$proxy=$null;$proxyProcess=$null
$ports=[ordered]@{'auth-service'=21081;'content-service'=21082;'review-service'=21083;'search-service'=21084;'file-service'=21085;'notification-service'=21086;'gateway-service'=21080}
$mavenArgs=@('-B','-ntp',"-Ds3.buildRoot=$BuildRoot")
$t=$null;$e=$null;$ast=[Management.Automation.Language.Parser]::ParseFile((Join-Path $PSScriptRoot 's4-acceptance.ps1'),[ref]$t,[ref]$e)
if($e.Count){throw 'S4 harness parse error'}
# Reuse the audited initializer, fixture guards and immutable jar snapshots only.
foreach($f in $ast.FindAll({param($n)$n -is [Management.Automation.Language.FunctionDefinitionAst]},$false)){
    $definition=$f.Extent.Text.Replace('$PSScriptRoot','$script:suiteScripts').Replace("'127.0.0.1::6379'",'"127.0.0.1:${script:redisPort}:6379"')
    . ([scriptblock]::Create($definition))
}
function Start-Instance([string]$Id,[string]$Module,[int]$Port){
    if(Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue){throw "Port $Port occupied"}
    Set-RunEnv 'SERVER_PORT' "$Port";Set-RunEnv 'PLATFORM_EVENTS_PUBLISHER_OWNER' $Id
    $args=@('-Xms48m','-Xmx256m','-XX:ActiveProcessorCount=2','-Duser.timezone=Asia/Shanghai',"`"-Ds5r.owner=$run`"","-Ds5r.instance=$Id",'-jar',"`"$(Jar $Module)`"",'--logging.level.root=INFO')
    $p=Start-Process -FilePath (Get-Command java -CommandType Application|Select-Object -First 1).Source -ArgumentList $args -WorkingDirectory $repo -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $run "$Id.out.log") -RedirectStandardError (Join-Path $run "$Id.err.log")
    $proc=Get-CimInstance Win32_Process -Filter "ProcessId=$($p.Id)"
    $script:owned+=@{pid=$p.Id;instance=$Id;service=$Module;port=$Port;createdAt=$proc.CreationDate.ToUniversalTime().ToString('o')}
    $deadline=(Get-Date).AddSeconds(150)
    do{
        if(-not(Get-Process -Id $p.Id -ErrorAction SilentlyContinue)){throw "$Id exited"}
        try{$health=Invoke-RestMethod "http://127.0.0.1:$Port/actuator/health/readiness" -TimeoutSec 3;if($health.status -eq 'UP'){Check "$Id readiness" $true;return}}catch{}
        Start-Sleep -Milliseconds 700
    }while((Get-Date) -lt $deadline)
    throw "$Id readiness timeout"
}
function Stop-Instance([string]$Id){
    foreach($o in @($script:owned|Where-Object {$_.instance -eq $Id})){
        $p=Get-CimInstance Win32_Process -Filter "ProcessId=$($o.pid)"
        if(-not $p){continue}
        if($p.Name -ne 'java.exe' -or $p.CommandLine -notlike "*-Ds5r.owner=$run*" -or $p.CommandLine -notlike "*-Ds5r.instance=$Id *" -or $p.CreationDate.ToUniversalTime() -ne ([datetime]$o.createdAt).ToUniversalTime()){throw 'PID owner mismatch'}
        Stop-Process -Id $p.ProcessId
        @{at=(Get-Date).ToString('o');action='stop';instance=$Id}|ConvertTo-Json -Compress|Add-Content (Join-Path $run 'faults.jsonl')
    }
}
function Fault([string]$Target,[string]$Action){
    Invoke-RestMethod "http://127.0.0.1:$($proxy.controlPort)/$Target/$Action" -Method Post -Headers @{Authorization="Bearer $script:proxyToken"}|Out-Null
    @{at=(Get-Date).ToString('o');target=$Target;action=$Action}|ConvertTo-Json -Compress|Add-Content (Join-Path $run 'faults.jsonl')
}
function Begin-Request([int]$Port,[string]$Method,[string]$Path,$Body=$null,[string]$Token='',[hashtable]$Headers=@{}){
    $r=[Net.Http.HttpRequestMessage]::new([Net.Http.HttpMethod]::new($Method),"http://127.0.0.1:$Port$Path")
    $r.Headers.Add('Origin','http://localhost:15173')
    if($Token){$r.Headers.Authorization=[Net.Http.Headers.AuthenticationHeaderValue]::new('Bearer',$Token)}
    foreach($key in $Headers.Keys){$r.Headers.TryAddWithoutValidation($key,[string]$Headers[$key])|Out-Null}
    if($null -ne $Body){$r.Content=[Net.Http.StringContent]::new(($Body|ConvertTo-Json -Depth 12 -Compress),[Text.Encoding]::UTF8,'application/json')}
    return @{request=$r;task=$script:http.SendAsync($r);path=$Path;port=$Port;watch=[Diagnostics.Stopwatch]::StartNew()}
}
function End-Request($Pending){
    $response=$null
    try{
        $response=$Pending.task.GetAwaiter().GetResult();$text=$response.Content.ReadAsStringAsync().GetAwaiter().GetResult();$json=$null
        if($text){try{$json=$text|ConvertFrom-Json -Depth 22}catch{}}
        $cookie=if($response.Headers.Contains('Set-Cookie')){@($response.Headers.GetValues('Set-Cookie'))}else{@()}
        $retry=if($response.Headers.Contains('Retry-After')){[string](@($response.Headers.GetValues('Retry-After'))[0])}else{''}
        return @{status=[int]$response.StatusCode;json=$json;cookies=$cookie;retryAfter=$retry;ms=$Pending.watch.Elapsed.TotalMilliseconds}
    }finally{
        $Pending.watch.Stop()
        @{at=(Get-Date).ToString('o');port=$Pending.port;path=$Pending.path;status=if($response){[int]$response.StatusCode}else{0};ms=[Math]::Round($Pending.watch.Elapsed.TotalMilliseconds,2)}|ConvertTo-Json -Compress|Add-Content (Join-Path $run 'http-receipts.jsonl')
        if($response){$response.Dispose()};$Pending.request.Dispose()
    }
}
function Request([int]$Port,[string]$Method,[string]$Path,$Body=$null,[string]$Token='',[hashtable]$Headers=@{}){End-Request (Begin-Request $Port $Method $Path $Body $Token $Headers)}
function Api([string]$Method,[string]$Path,$Body=$null,[string]$Token='', [hashtable]$ExtraHeaders=@{}){Request 21080 $Method $Path $Body $Token $ExtraHeaders}
function Cookie($Response){[string](@($Response.cookies|Where-Object {$_ -like "${db}_refresh=*"})[0].Split(';')[0])}
function Login([string]$Name,[int]$Port=21081){
    $r=Request $Port POST '/api/v1/auth/login' @{account=$Name;password=$env:S4_LOGIN_PASSWORD;rememberMe=$false}
    Check "$Name login on $Port" ($r.status -eq 200)
    return @{token=$r.json.data.token;cookie=(Cookie $r)}
}
function Validate([int]$Port,[string]$Token){Request $Port POST '/internal/auth/session/validate' @{token=$Token} '' @{'X-Internal-Token'=$env:INTERNAL_TOKEN}}
. (Join-Path $PSScriptRoot 's5-recovery-flows.ps1')
Push-Location $repo;$success=$false
try{
    Prepare-Run
    $script:proxyToken=[Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
    @{token=$script:proxyToken;targets=@{mysql=@{host='127.0.0.1';port=13306};rabbit=@{host='127.0.0.1';port=15673}}}|ConvertTo-Json -Depth 5|Set-Content (Join-Path $run 'proxy-secret.json')
    $proxyProcess=Start-Process -FilePath (Get-Command node -CommandType Application|Select-Object -First 1).Source -ArgumentList @("`"$(Join-Path $PSScriptRoot 's5-fault-proxy.mjs')`"","`"$(Join-Path $run 'proxy-secret.json')`"","`"$(Join-Path $run 'proxy-state.json')`"") -WorkingDirectory $repo -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $run 'proxy-events.log') -RedirectStandardError (Join-Path $run 'proxy.err.log')
    $deadline=(Get-Date).AddSeconds(10);while(-not(Test-Path (Join-Path $run 'proxy-state.json'))){if((Get-Date) -gt $deadline){throw 'Proxy startup failed'};Start-Sleep -Milliseconds 200}
    $proxy=Get-Content (Join-Path $run 'proxy-state.json') -Raw|ConvertFrom-Json
    Set-RunEnv 'DB_URL' "jdbc:mysql://127.0.0.1:$($proxy.targets.mysql.port)/${db}?connectionTimeZone=Asia/Shanghai&forceConnectionTimeZoneToSession=true&connectTimeout=1500&socketTimeout=3000&allowPublicKeyRetrieval=true&useSSL=false"
    Set-RunEnv 'RABBITMQ_PORT' "$($proxy.targets.rabbit.port)"
    Set-RunEnv 'SPRING_DATASOURCE_HIKARI_CONNECTION_TIMEOUT' '1500'
    Set-RunEnv 'SPRING_DATASOURCE_HIKARI_VALIDATION_TIMEOUT' '1000'
    Set-RunEnv 'PLATFORM_RATE_LIMIT_SEARCH_MAX_REQUESTS' '6'
    Set-RunEnv 'PLATFORM_STORAGE_MAX_UPLOADS_PER_USER_PER_DAY' '6'
    Set-RunEnv 'PLATFORM_GATEWAY_RATE_LIMIT_REPLENISH_RATE' '200'
    Set-RunEnv 'PLATFORM_GATEWAY_RATE_LIMIT_BURST_CAPACITY' '400'
    Set-RunEnv 'TRUSTED_PROXIES' '127.0.0.1/32'
    Set-RunEnv 'SPRING_RABBITMQ_CONNECTION_TIMEOUT' '1500'
    Set-RunEnv 'SPRING_RABBITMQ_REQUESTED_HEARTBEAT' '5'
    foreach($entry in $ports.GetEnumerator()){Start-Instance ($entry.Key+'-a') $entry.Key $entry.Value}
    foreach($module in @('auth-service','content-service','review-service','notification-service','gateway-service')){Start-Instance ($module+'-b') $module ($ports[$module]+100)}
    $handler=[Net.Http.HttpClientHandler]::new();$handler.UseCookies=$false
    $script:http=[Net.Http.HttpClient]::new($handler);$script:http.Timeout=[TimeSpan]::FromSeconds(12)
    Run-Recovery
    $success=$true
}catch{
    @{message=$_.Exception.Message;stack=$_.ScriptStackTrace}|ConvertTo-Json|Set-Content (Join-Path $run 'failure.json')
    throw
}finally{
    $receipt=@{at=(Get-Date).ToString('o');success=$success;scope='S5_RECOVERY_ONLY';run=$run;schema=$db;namespace=$namespace;checks=$checks;processes=$owned;proxy=$proxy;productionTouched=$false;s5FullAcceptance=$false}
    $receipt|ConvertTo-Json -Depth 9|Set-Content (Join-Path $run 'receipt.json')
    if(-not($KeepServices -and $success)){
        foreach($id in @($owned|ForEach-Object {$_.instance}|Select-Object -Unique)){Stop-Instance $id}
        if($proxyProcess){$p=Get-CimInstance Win32_Process -Filter "ProcessId=$($proxyProcess.Id)";if($p -and $p.CommandLine -like '*s5-fault-proxy.mjs*' -and $p.CommandLine -like "*$run*"){Stop-Process -Id $p.ProcessId}}
        if($redisContainer){$label=& docker inspect --format '{{index .Config.Labels "semi-overt.s4.owner"}}' $redisContainer 2>$null;if($LASTEXITCODE -eq 0 -and $label -eq $db){& docker stop $redisContainer|Out-Null}}
    }
    if(Get-Variable -Name http -Scope Script -ErrorAction SilentlyContinue){$script:http.Dispose()}
    foreach($name in $saved.Keys){[Environment]::SetEnvironmentVariable($name,$saved[$name],'Process')}
    Pop-Location
    Write-Host "Recovery receipts: $run; success=$success; not S5 full acceptance"
}
