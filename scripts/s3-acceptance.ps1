#requires -Version 7.0
[CmdletBinding()]
param([switch]$SkipBuild,[string]$MavenSettings='',[string]$BuildRoot='', [switch]$KeepServices, [switch]$SkipRedisFault)
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
if($PSVersionTable.PSEdition -ne 'Core'){throw 'pwsh Core required'}
$repo=Split-Path -Parent $PSScriptRoot
if(-not $BuildRoot){$BuildRoot=Join-Path $repo '.runtime/s3/build'}
$run=Join-Path $repo ('.runtime/s3/accept-'+(Get-Date -Format 'yyyyMMdd-HHmmss'))
New-Item -ItemType Directory -Path $run -Force | Out-Null
$db='s3_accept_'+[guid]::NewGuid().ToString('N').Substring(0,16)
$namespace=$db
$saved=@{};$owned=@();$checks=[Collections.Generic.List[object]]::new();$redisContainer=$null
$ports=[ordered]@{'auth-service'=19081;'content-service'=19082;'review-service'=19083;'search-service'=19084;'file-service'=19085;'notification-service'=19086;'gateway-service'=19080}
$mavenArgs=@('-B','-ntp',"-Ds3.buildRoot=$BuildRoot");if($MavenSettings){$mavenArgs+=@('-s',$MavenSettings)}
function Set-RunEnv([string]$Name,[AllowEmptyString()][string]$Value){
    if(-not $saved.ContainsKey($Name)){$saved[$Name]=[Environment]::GetEnvironmentVariable($Name,'Process')}
    [Environment]::SetEnvironmentVariable($Name,$Value,'Process')
}
function Check([string]$Name,[bool]$Pass){
    $checks.Add(@{name=$Name;pass=$Pass;at=(Get-Date).ToString('o')})
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
    $output=@(& java -Xmx128m -cp $script:fixtureClasspath S3DatabaseFixture query $Sql)
    if($LASTEXITCODE -ne 0){throw 'Fixture read-only query failed'};return ,$output
}
function Start-Api([string]$Method,[string]$Path,$Body=$null,[string]$Token='', [hashtable]$ExtraHeaders=@{}){
    $request=[Net.Http.HttpRequestMessage]::new([Net.Http.HttpMethod]::new($Method),'http://127.0.0.1:19080'+$Path)
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
    Set-RunEnv 'S3_ACCEPTANCE_DB' $db
    Set-RunEnv 'S3_MYSQL_PASSWORD' $env:S5_MYSQL_ROOT_PASSWORD
    Set-RunEnv 'S3_LOGIN_PASSWORD' ([Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(24)))
    [IO.File]::WriteAllText((Join-Path $run 'fixture-password'),$env:S3_LOGIN_PASSWORD,[Text.UTF8Encoding]::new($false))
    $deps=Join-Path $run 'auth-classpath.txt'
    & mvn @mavenArgs -pl auth-service dependency:build-classpath "-Dmdep.outputFile=$deps" *> (Join-Path $run 'classpath.log')
    if($LASTEXITCODE -ne 0){throw 'Dependency classpath failed'}
    $script:fixtureClasspath=[IO.File]::ReadAllText($deps).Trim()
    & javac -proc:none -encoding UTF-8 -cp $script:fixtureClasspath -d $run (Join-Path $PSScriptRoot 'S3DatabaseFixture.java')
    if($LASTEXITCODE -ne 0){throw 'Acceptance fixture compile failed'}
    $script:fixtureClasspath=$run+';'+$script:fixtureClasspath
    & java -Xmx128m -cp $script:fixtureClasspath S3DatabaseFixture create
    if($LASTEXITCODE -ne 0){throw 'Isolated fixture DB creation failed'}
    Set-RunEnv 'DB_URL' "jdbc:mysql://127.0.0.1:13306/${db}?connectionTimeZone=Asia/Shanghai&forceConnectionTimeZoneToSession=true&useUnicode=true&characterEncoding=utf8&allowPublicKeyRetrieval=true&useSSL=false"
    Set-RunEnv 'DB_USERNAME' 'root';Set-RunEnv 'DB_PASSWORD' $env:S5_MYSQL_ROOT_PASSWORD;Set-RunEnv 'MIGRATION_MODE' 'AUTO'
    & java -Xmx256m -jar (Jar 'db-migration') *> (Join-Path $run 'migration.log')
    if($LASTEXITCODE -ne 0){throw 'Isolated fixture migration failed'}
    & java -Xmx128m -cp $script:fixtureClasspath S3DatabaseFixture seed
    if($LASTEXITCODE -ne 0){throw 'Isolated fixture seeding failed'}
    $script:rabbitHeaders=@{Authorization='Basic '+[Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($env:RABBITMQ_USERNAME+':'+$env:RABBITMQ_PASSWORD))}
    Invoke-RestMethod "http://127.0.0.1:15683/api/vhosts/$namespace" -Method Put -Headers $script:rabbitHeaders -ContentType 'application/json' -Body '{}'|Out-Null
    Invoke-RestMethod "http://127.0.0.1:15683/api/permissions/$namespace/$($env:RABBITMQ_USERNAME)" -Method Put -Headers $script:rabbitHeaders -ContentType 'application/json' -Body '{"configure":".*","write":".*","read":".*"}'|Out-Null
    Invoke-RestMethod 'http://127.0.0.1:18848/nacos/v1/console/namespaces' -Method Post -Body @{customNamespaceId=$namespace;namespaceName=$namespace;namespaceDesc='S3 isolated acceptance'}|Out-Null
    Set-RunEnv 'NACOS_NAMESPACE' $namespace;Set-RunEnv 'SPRING_RABBITMQ_VIRTUAL_HOST' $namespace
    Set-RunEnv 'NACOS_SERVER_ADDR' '127.0.0.1:18848';Set-RunEnv 'SPRING_CLOUD_NACOS_DISCOVERY_IP' '127.0.0.1'
    Set-RunEnv 'SERVER_ADDRESS' '127.0.0.1';Set-RunEnv 'RABBITMQ_HOST' '127.0.0.1';Set-RunEnv 'RABBITMQ_PORT' '15673'
    $script:redisContainer=$db.Replace('_','-')+'-redis'
    & docker run -d --name $script:redisContainer --label "semi-overt.s3.owner=$db" -p '127.0.0.1::6379' redis:7.2.7 *> (Join-Path $run 'redis-start.log')
    if($LASTEXITCODE -ne 0){throw 'Isolated acceptance Redis startup failed'}
    $redisPort=& docker inspect --format '{{(index (index .NetworkSettings.Ports "6379/tcp") 0).HostPort}}' $script:redisContainer
    Set-RunEnv 'REDIS_HOST' '127.0.0.1';Set-RunEnv 'REDIS_PORT' $redisPort.Trim();Set-RunEnv 'REDIS_DB' '0'
    Set-RunEnv 'AUTH_REFRESH_COOKIE_NAME' ($db+'_refresh');Set-RunEnv 'SPRING_PROFILES_ACTIVE' 's3accept'
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
        $arguments=@('-Xms64m','-Xmx256m','-XX:ActiveProcessorCount=2','-Duser.timezone=Asia/Shanghai',"`"-Ds3.owner=$run`"",'-jar',"`"$(Jar $entry.Key)`"")
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
        if($p -and $p.Name -eq 'java.exe' -and $p.CommandLine -like "*-Ds3.owner=$run*" -and $p.CommandLine -like "*$($item.service)*") {
            Stop-Process -Id $p.ProcessId
        }
    }
}
function Run-GenerationFlows($Tokens,[string]$Body) {
    $writer=$Tokens.s3writer;$admin=$Tokens.s3admin
    $id=New-Draft $writer
    Check 'save generation fixture' ((Api PUT "/api/v1/articles/$id/draft" @{title='generation fixture';content=$Body;version=0} $writer).status -eq 200)
    Check 'first submission' ((Api POST "/api/v1/articles/$id/submit" $null $writer).status -eq 200)
    Wait-Until 'first task generation exists' {[int](DbQuery "SELECT COUNT(*) FROM review_tasks WHERE article_id=$id AND status='PENDING'")[0] -eq 1}
    $old=(DbQuery "SELECT CONCAT(submission_id,':',version) FROM articles WHERE id=$id")[0]
    $oldSubmission=$old.Split(':')[0]
    $oldVersion=[long]$old.Split(':')[1]
    Check 'cancel review authoritatively' ((Api POST "/api/v1/articles/$id/cancel-review" $null $writer).status -eq 200)
    $cancel=(DbQuery "SELECT payload FROM event_outbox WHERE aggregate_id='$id' AND event_type='ArticleStatusChangedEvent' AND JSON_UNQUOTE(JSON_EXTRACT(payload,'$.toStatus'))='DRAFT' ORDER BY created_at DESC LIMIT 1")[0]
    Check 'immediate resubmit no legacy cooldown' ((Api POST "/api/v1/articles/$id/submit" $null $writer).status -eq 200)
    Wait-Until 'second task generation projected' {[int](DbQuery "SELECT COUNT(*) FROM review_tasks WHERE article_id=$id AND status='PENDING' AND submission_id<>'$oldSubmission'")[0] -eq 1}
    $assigned=[int](DbQuery "SELECT assigned_admin_id FROM review_tasks WHERE article_id=$id")[0]
    $admin=$Tokens[@{103='s3admin';104='s3otheradmin';105='s3adminauthor'}[$assigned]]
    Check 'old review page cannot decide a new submission' ((Api POST "/api/v1/review/$id/decision" @{
        action='APPROVE';decisionId=[guid]::NewGuid().ToString();submissionId=$oldSubmission;expectedVersion=$oldVersion
    } $admin).status -eq 409)
    Publish-Replay 'article.status.changed.exchange' $cancel
    $oldEvent=$cancel|ConvertFrom-Json;$oldEvent.eventId=[guid]::NewGuid().ToString()
    Publish-Replay 'article.status.changed.exchange' ($oldEvent|ConvertTo-Json -Depth 10 -Compress)
    Start-Sleep -Seconds 2
    Check 'late cancel never closes new submission' ([int](DbQuery "SELECT COUNT(*) FROM review_tasks WHERE article_id=$id AND status='PENDING' AND submission_id<>'$oldSubmission'")[0] -eq 1)
    $oneKey=[guid]::NewGuid().ToString();$twoKey=[guid]::NewGuid().ToString()
    $a=Start-Api POST "/api/v1/review/$id/decision" @{action='RETURN';reason='revise';decisionId=$oneKey} $admin
    $b=Start-Api POST "/api/v1/review/$id/decision" @{action='APPROVE';decisionId=$twoKey} $admin
    $r=@((Finish-Api $a),(Finish-Api $b))
    Check 'concurrent opposing decisions exactly one final' ((@($r|Where-Object {$_.status -eq 200}).Count -eq 1) -and (@($r|Where-Object {$_.status -eq 409}).Count -eq 1))
    Check 'content authority one accepted decision per generation' ([int](DbQuery "SELECT COUNT(*) FROM content_review_decisions WHERE article_id=$id AND state='FINAL'")[0] -eq 1)
    $self=New-Draft $Tokens.s3adminauthor
    Check 'admin author draft save' ((Api PUT "/api/v1/articles/$self/draft" @{title='no self review';content=$Body;version=0} $Tokens.s3adminauthor).status -eq 200)
    Check 'admin author submit' ((Api POST "/api/v1/articles/$self/submit" $null $Tokens.s3adminauthor).status -eq 200)
    Wait-Until 'assignment excludes author' {[int](DbQuery "SELECT COUNT(*) FROM review_tasks WHERE article_id=$self AND assigned_admin_id<>105 AND status='PENDING'")[0] -eq 1}
    Check 'admin author self-review forbidden' ((Api POST "/api/v1/review/$self/decision" @{action='APPROVE';decisionId=[guid]::NewGuid().ToString()} $Tokens.s3adminauthor).status -eq 403)
    $before=[int](DbQuery "SELECT COUNT(*) FROM notifications")[0]
    Check 'admin delete CAS' ((Api DELETE "/api/v1/admin/articles/$self" $null $admin).status -eq 200)
    Wait-Until 'delete task tombstone projected' {[int](DbQuery "SELECT COUNT(*) FROM review_tasks WHERE article_id=$self AND command_state='CLOSED'")[0] -eq 1}
    Check 'deleted article private to all' ((Api GET "/api/v1/articles/$self" $null $Tokens.s3adminauthor).status -eq 404)
    Check 'delete not a fresh review notification' ([int](DbQuery "SELECT COUNT(*) FROM notifications WHERE biz_id=$self")[0] -eq 0)
}
function Run-UnknownRecovery($Tokens,[string]$Body) {
    $writer=$Tokens.s3writer;$id=New-Draft $writer
    Check 'unknown window fixture saved' ((Api PUT "/api/v1/articles/$id/draft" @{title='unknown recovery';content=$Body;version=0} $writer).status -eq 200)
    Check 'unknown window fixture submitted' ((Api POST "/api/v1/articles/$id/submit" $null $writer).status -eq 200)
    Wait-Until 'unknown fixture assigned' {[int](DbQuery "SELECT COUNT(*) FROM review_tasks WHERE article_id=$id AND assigned_admin_id IS NOT NULL AND status='PENDING'")[0] -eq 1}
    & java -Xmx128m -cp $script:fixtureClasspath S3DatabaseFixture lose-projection "$id"
    if($LASTEXITCODE -ne 0){throw 'Projection fault injection failed'}
    Wait-Until 'reconciliation repairs missing task from content authority' {[int](DbQuery "SELECT COUNT(*) FROM review_tasks WHERE article_id=$id AND assigned_admin_id IS NOT NULL AND status='PENDING'")[0] -eq 1} 70
    $assigned=[int](DbQuery "SELECT assigned_admin_id FROM review_tasks WHERE article_id=$id")[0]
    $admin=$Tokens[@{103='s3admin';104='s3otheradmin';105='s3adminauthor'}[$assigned]]
    $content=$owned|Where-Object {$_.service -eq 'content-service'}|Select-Object -Last 1
    $process=Get-CimInstance Win32_Process -Filter "ProcessId=$($content.pid)"
    if(-not $process -or $process.CommandLine -notlike "*-Ds3.owner=$run*"){throw 'Cannot verify owned content fault target'}
    Stop-Process -Id $process.ProcessId
    Wait-Process -Id $process.ProcessId -Timeout 20 -ErrorAction SilentlyContinue
    $key=[guid]::NewGuid().ToString()
    $r=Api POST "/api/v1/review/$id/decision" @{action='APPROVE';decisionId=$key} $admin
    Check 'unavailable authority never returns final success' ($r.status -eq 503)
    Check 'unknown command durably retained' ([int](DbQuery "SELECT COUNT(*) FROM review_commands WHERE decision_id='$key' AND state='PROCESSING'")[0] -eq 1)
    Start-OneService 'content-service' 19082
    $deadline=(Get-Date).AddSeconds(50);$final=$null
    do{
        $final=Api GET "/api/v1/review/$id/decision-status?decisionId=$key" $null $admin
        if($final.status -eq 200 -and $final.json.data.state -eq 'FINAL'){break}
        Start-Sleep -Seconds 1
    }while((Get-Date) -lt $deadline)
    Check 'unknown resolves via same persisted key after authority restart' ($final.status -eq 200 -and $final.json.data.state -eq 'FINAL')
    Check 'recovered command exactly one content final and log' ([int](DbQuery "SELECT (SELECT COUNT(*) FROM content_review_decisions WHERE decision_id='$key' AND state='FINAL')+(SELECT COUNT(*) FROM review_logs WHERE decision_id='$key')")[0] -eq 2)
}
function Run-Flows {
    $tokens=@{}
    foreach($name in @('s3writer','s3visitor','s3admin','s3otheradmin','s3adminauthor')){
        $r=Api POST '/api/v1/auth/login' @{account=$name;password=$env:S3_LOGIN_PASSWORD;rememberMe=$false}
        Check "durable login $name" ($r.status -eq 200)
        $tokens[$name]=$r.json.data.token
    }
    $writer=$tokens.s3writer;$visitor=$tokens.s3visitor;$admin=$tokens.s3admin;$other=$tokens.s3otheradmin
    $id=New-Draft $writer
    Check 'anonymous draft privacy' ((Api GET "/api/v1/articles/$id").status -eq 404)
    $draft=Api GET "/api/v1/articles/$id" $null $writer
    Check 'initial draft version' ($draft.json.data.version -eq 0)
    $body='正文测试用于跨服务审核一致性与数据库持久化验证。'*8
    $one=Start-Api PUT "/api/v1/articles/$id/draft" @{title='same version first';content=$body;version=0} $writer
    $two=Start-Api PUT "/api/v1/articles/$id/draft" @{title='same version second';content=$body;version=0} $writer
    $responses=@((Finish-Api $one),(Finish-Api $two))
    Check 'same-baseline concurrent saves one success one conflict' ((@($responses|Where-Object {$_.status -eq 200}).Count -eq 1) -and (@($responses|Where-Object {$_.status -eq 409}).Count -eq 1))
    $winner=($responses|Where-Object {$_.status -eq 200}).json.data
    Check 'server save receipt includes version and updatedAt' ($winner.version -eq 1 -and $null -ne $winner.updatedAt)
    $r=Api PUT "/api/v1/articles/$id/draft" @{summary='summary';version=1} $writer
    Check 'draft partial patch' ($r.status -eq 200)
    $r=Api PUT "/api/v1/articles/$id/draft" @{summary='';content=$null;version=2} $writer
    Check 'explicit clear and null-preserve patch' ($r.status -eq 200)
    $detail=Api GET "/api/v1/articles/$id" $null $writer
    Check 'null retained body and empty summary cleared' ($detail.json.data.content -eq $body -and [string]$detail.json.data.summary -eq '')
    Check '15001 counted characters rejected' ((Api PUT "/api/v1/articles/$id/draft" @{content=('文'*15001);version=3} $writer).status -eq 400)
    $r=Api POST "/api/v1/articles/$id/submit" $null $writer
    Check 'submit authoritatively pending' ($r.status -eq 200 -and $r.json.data.status -eq 'PENDING')
    Wait-Until 'review task projected and assigned' { [int](DbQuery "SELECT COUNT(*) FROM review_tasks WHERE article_id=$id AND assigned_admin_id IS NOT NULL AND status='PENDING'")[0] -eq 1 }
    $assigned=[int](DbQuery "SELECT assigned_admin_id FROM review_tasks WHERE article_id=$id")[0]
    $admin=$tokens[@{103='s3admin';104='s3otheradmin';105='s3adminauthor'}[$assigned]]
    $other=if($assigned -eq 103){$tokens.s3otheradmin}else{$tokens.s3admin}
    Check 'other admin cannot read unassigned pending body' ((Api GET "/api/v1/articles/$id" $null $other).status -in @(403,404))
    Check 'assigned admin can read pending body' ((Api GET "/api/v1/articles/$id" $null $admin).status -eq 200)
    Check 'other admin cannot decide' ((Api POST "/api/v1/review/$id/decision" @{action='APPROVE';decisionId=[guid]::NewGuid().ToString()} $other).status -eq 403)
    $decision=[guid]::NewGuid().ToString()
    $first=Api POST "/api/v1/review/$id/decision" @{action='APPROVE';decisionId=$decision} $admin @{'Idempotency-Key'=$decision}
    Check 'decision response only after authority final' ($first.status -eq 200 -and $first.json.data.status -eq 'APPROVED' -and $first.json.data.decisionId -eq $decision -and $null -ne $first.json.data.updatedAt)
    $again=Api POST "/api/v1/review/$id/decision" @{action='APPROVE';decisionId=$decision} $admin @{'Idempotency-Key'=$decision}
    Check 'same decision retry returns original final' ($again.status -eq 200 -and $again.json.data.updatedAt -eq $first.json.data.updatedAt)
    Check 'same key changed payload conflicts' ((Api POST "/api/v1/review/$id/decision" @{action='RETURN';reason='changed';decisionId=$decision} $admin).status -eq 409)
    $status=Api GET "/api/v1/review/$id/decision-status?decisionId=$decision" $null $admin
    Check 'decision status alias returns FINAL' ($status.status -eq 200 -and $status.json.data.state -eq 'FINAL')
    Wait-Until 'unique final log and notification committed' { [int](DbQuery "SELECT (SELECT COUNT(*) FROM review_logs WHERE decision_id='$decision')+(SELECT COUNT(*) FROM notifications WHERE decision_id='$decision')")[0] -eq 2 }
    $confirmedPayload=(DbQuery "SELECT payload FROM event_outbox WHERE aggregate_id='$id' AND event_type='ArticleStatusChangedEvent' AND JSON_UNQUOTE(JSON_EXTRACT(payload,'$.decisionId'))='$decision' LIMIT 1")[0]
    Publish-Replay 'article.status.changed.exchange' $confirmedPayload
    $duplicate=$confirmedPayload|ConvertFrom-Json;$duplicate.eventId=[guid]::NewGuid().ToString()
    Publish-Replay 'article.status.changed.exchange' ($duplicate|ConvertTo-Json -Depth 10 -Compress)
    Start-Sleep -Seconds 2
    Check 'duplicate accepted results create one log and notification' ([int](DbQuery "SELECT (SELECT COUNT(*) FROM review_logs WHERE decision_id='$decision')+(SELECT COUNT(*) FROM notifications WHERE decision_id='$decision')")[0] -eq 2)
    Check 'approved article public' ((Api GET "/api/v1/articles/$id").status -eq 200)
    # Remaining generation/failure cases live in Run-GenerationFlows.
    Run-GenerationFlows $tokens $body
    Run-UnknownRecovery $tokens $body
    & java -Xmx128m -cp $script:fixtureClasspath S3DatabaseFixture seed-draft-limit
    if($LASTEXITCODE -ne 0){throw 'Draft limit fixture failed'}
    $a=Start-Api POST '/api/v1/articles' $null $visitor
    $b=Start-Api POST '/api/v1/articles' $null $visitor
    $limitResults=@((Finish-Api $a),(Finish-Api $b))
    Check '100 draft limit serializes competing creations' ((@($limitResults|Where-Object {$_.status -eq 200}).Count -eq 1) -and (@($limitResults|Where-Object {$_.status -eq 409}).Count -eq 1))
    Check 'no 101st draft committed' ([int](DbQuery "SELECT COUNT(*) FROM articles WHERE author_id=102 AND deleted=0")[0] -eq 100)
    # Last fault: gateway intentionally depends on Redis; content persistence must not.
    if($SkipRedisFault){return}
    $cacheDraft=New-Draft $writer
    & docker stop $redisContainer *> (Join-Path $run 'redis-fault.log')
    if($LASTEXITCODE -ne 0){throw 'Owned Redis fault injection failed'}
    $direct=Invoke-WebRequest "http://127.0.0.1:19082/api/v1/articles/$cacheDraft/draft" -Method Put -Headers @{
        'X-Internal-Token'=$env:INTERNAL_TOKEN;'X-User-Id'='101';'X-User-Role'='USER';'X-Username'='s3writer'
    } -ContentType 'application/json' -Body (@{summary='database persists during Redis outage';version=0}|ConvertTo-Json) -SkipHttpErrorCheck -TimeoutSec 15
    Check 'content draft save survives Redis outage' ([int]$direct.StatusCode -eq 200)
}
Push-Location $repo
$success=$false
try{Prepare-Run;Start-Services;Run-Flows;$success=$true}
finally{
    @{at=(Get-Date).ToString('o');schema=$db;namespace=$namespace;run=$run;checks=$checks;processes=$owned;productionTouched=$false}|ConvertTo-Json -Depth 8|Set-Content -LiteralPath (Join-Path $run 'receipt.json') -Encoding utf8
    if(-not ($KeepServices -and $success)){Stop-OwnedServices}
    if($redisContainer -and -not ($KeepServices -and $success)){
        $label=& docker inspect --format '{{index .Config.Labels "semi-overt.s3.owner"}}' $redisContainer 2>$null
        if($LASTEXITCODE -eq 0 -and $label -eq $db){& docker stop $redisContainer *> (Join-Path $run 'redis-stop.log')}
    }
    if(Get-Variable -Name http -Scope Script -ErrorAction SilentlyContinue){$script:http.Dispose()}
    foreach($name in $saved.Keys){[Environment]::SetEnvironmentVariable($name,$saved[$name],'Process')}
    Pop-Location
    Write-Host "Acceptance receipts retained: $run (credentials not printed)"
}
