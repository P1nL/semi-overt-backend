function Hash([string]$Text){[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($Text))).ToLowerInvariant()}
function InternalHeaders([long]$User=101,[string]$Role='USER'){@{'X-Internal-Token'=$env:INTERNAL_TOKEN;'X-User-Id'="$User";'X-User-Role'=$Role;'X-Username'="fixture$User"}}
function Seed-Review([string]$Token){
    $id=New-Draft $Token
    Check 'review fixture saved' ((Request 21080 PUT "/api/v1/articles/$id/draft" @{title='Recovery fixture';content=('真实故障恢复测试正文。'*20);version=0} $Token).status -eq 200)
    Check 'review fixture submitted' ((Request 21180 POST "/api/v1/articles/$id/submit" $null $Token).status -eq 200)
    return $id
}
function Run-Recovery {
    foreach($module in @('auth-service','gateway-service','content-service','review-service','notification-service')){
        $instances=Invoke-RestMethod "http://127.0.0.1:18848/nacos/v1/ns/instance/list?serviceName=$module&groupName=NOW_DEMO&namespaceId=$namespace"
        $hosts=@($instances.hosts|Where-Object {$_.healthy})
        Check "Nacos has two distinct healthy $module processes" ($hosts.Count -eq 2 -and @($hosts.port|Select-Object -Unique).Count -eq 2)
    }
    $session=Login 's4writer' 21081
    Check 'auth B validates auth A login' ((Validate 21181 $session.token).status -eq 200)
    $refreshed=Request 21181 POST '/api/v1/auth/refresh' $null '' @{Cookie=$session.cookie}
    Check 'auth B rotates A refresh cookie' ($refreshed.status -eq 200 -and (Cookie $refreshed) -ne $session.cookie)
    $session=@{token=$refreshed.json.data.token;cookie=(Cookie $refreshed)}
    Check 'auth A validates B refreshed token' ((Validate 21081 $session.token).status -eq 200)
    Check 'auth A logs out B refreshed session' ((Request 21081 POST '/api/v1/auth/logout' $null '' @{Cookie=$session.cookie}).status -eq 200)
    Check 'revocation visible on both auth instances' ((Validate 21081 $session.token).status -eq 401 -and (Validate 21181 $session.token).status -eq 401)
    $session=Login 's4writer';$visitor=Login 's4visitor';$admin=Login 's4admin';$other=Login 's4otheradmin'
    $script:tokens=@{101=$session.token;102=$visitor.token;103=$admin.token;104=$other.token}
    $internal=@{'X-Internal-Token'=$env:INTERNAL_TOKEN}
    $pending=@(0..19|ForEach-Object {Begin-Request (21081+100*($_%2)) POST '/internal/auth/budget/consume' @{clientIp='192.0.2.8';operation='SEARCH'} '' $internal})
    $results=@($pending|ForEach-Object {End-Request $_})
    Check '20 concurrent calls across two auth processes share exact six-request quota' (@($results|Where-Object {$_.status -eq 200 -and $_.json.data.allowed}).Count -eq 6 -and @($results|Where-Object {$_.status -eq 200 -and -not $_.json.data.allowed}).Count -eq 14)
    $key=Hash '192.0.2.8'
    Check 'shared quota database count is six not twelve' ([int](DbQuery "SELECT request_count FROM rate_limit_buckets WHERE bucket_key='search:ip:$key'")[0] -eq 6)
    $pending=@(0..11|ForEach-Object {Begin-Request (21080+100*($_%2)) GET '/api/v1/search?keyword=nebula' $null '' @{'X-Forwarded-For'='192.0.2.9'}})
    $results=@($pending|ForEach-Object {End-Request $_})
    Check 'two gateways expose shared search quota as six 200 and six 429' (@($results|Where-Object {$_.status -eq 200}).Count -eq 6 -and @($results|Where-Object {$_.status -eq 429}).Count -eq 6)
    Check 'quota rejection carries retry timing' (@($results|Where-Object {$_.status -eq 429 -and [int]$_.retryAfter -ge 1}).Count -eq 6)
    Run-Uploads $visitor.token
    $id=New-Draft $session.token
    $p1=Begin-Request 21082 PUT "/api/v1/articles/$id/draft" @{title='concurrent A';version=0} '' (InternalHeaders)
    $p2=Begin-Request 21182 PUT "/api/v1/articles/$id/draft" @{title='concurrent B';version=0} '' (InternalHeaders)
    $cas=@((End-Request $p1),(End-Request $p2))
    Check 'two content processes same-version save yields exactly 200 and 409' (@($cas|Where-Object {$_.status -eq 200}).Count -eq 1 -and @($cas|Where-Object {$_.status -eq 409}).Count -eq 1)
    Check 'concurrent save advances database version once' ([int](DbQuery "SELECT version FROM articles WHERE id=$id")[0] -eq 1)
    Run-BrokerRecovery $session.token
    Run-AuthorityRecovery $session.token
    Run-DatabaseRecovery $session
    Run-RedisRecovery $session.token
    Run-Load
    $resources=@(foreach($o in $owned){$p=Get-Process -Id $o.pid -ErrorAction SilentlyContinue;if($p){@{instance=$o.instance;rssMiB=[Math]::Round($p.WorkingSet64/1MB,1)}}})
    $resources|ConvertTo-Json|Set-Content (Join-Path $run 'instance-memory.json')
}
function Run-Uploads([string]$Token){
    [byte[]]$png=[Convert]::FromBase64String('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jBz0AAAAASUVORK5CYII=')
    $results=@(foreach($i in 0..7){
        $port=21080+100*($i%2);$r=[Net.Http.HttpRequestMessage]::new([Net.Http.HttpMethod]::Post,"http://127.0.0.1:$port/api/v1/upload")
        $form=[Net.Http.MultipartFormDataContent]::new();$file=[Net.Http.ByteArrayContent]::new($png);$file.Headers.ContentType=[Net.Http.Headers.MediaTypeHeaderValue]::new('image/png')
        $form.Add($file,'file','pixel.png');$form.Add([Net.Http.StringContent]::new('AVATAR'),'bizType');$r.Content=$form
        $r.Headers.Authorization=[Net.Http.Headers.AuthenticationHeaderValue]::new('Bearer',$Token)
        End-Request @{request=$r;task=$script:http.SendAsync($r);path='/api/v1/upload';port=$port;watch=[Diagnostics.Stopwatch]::StartNew()}
    })
    Check 'alternating gateways cannot double daily upload quota' (@($results|Where-Object {$_.status -eq 200}).Count -eq 6 -and @($results|Where-Object {$_.status -eq 429}).Count -eq 2)
    Check 'rejected uploads do not create extra files' (@(Get-ChildItem -LiteralPath (Join-Path $run 'uploads') -Recurse -File).Count -eq 6)
}
function Run-BrokerRecovery([string]$Token){
    Fault rabbit cut
    try{
        $ids=@(foreach($i in 0..3){Seed-Review $Token})
        $set=$ids -join ','
        Check 'broker outage retains transactional events without false published status' ([int](DbQuery "SELECT COUNT(*) FROM event_outbox WHERE aggregate_id IN ($set) AND status<>'PUBLISHED'")[0] -ge 4)
        Stop-Instance 'content-service-a'
    }finally{Fault rabbit restore}
    $timer=[Diagnostics.Stopwatch]::StartNew()
    Wait-Until 'remaining content publisher drains durable outbox after broker recovery' {[int](DbQuery "SELECT COUNT(*) FROM event_outbox WHERE aggregate_id IN ($set) AND status<>'PUBLISHED'")[0] -eq 0} 90
    $timer.Stop();@{scenario='broker';recoveryMs=$timer.ElapsedMilliseconds}|ConvertTo-Json -Compress|Add-Content (Join-Path $run 'recovery-times.jsonl')
    Start-Instance 'content-service-a' 'content-service' 21082
    $id=$ids[0]
    Wait-Until 'review assignment available after broker recovery' {[int](DbQuery "SELECT COUNT(*) FROM review_tasks WHERE article_id=$id AND assigned_admin_id IS NOT NULL")[0] -eq 1} 90
    $assigned=[int](DbQuery "SELECT assigned_admin_id FROM review_tasks WHERE article_id=$id")[0];$key=[guid]::NewGuid().ToString()
    $h=InternalHeaders $assigned 'ADMIN'
    $a=Begin-Request 21083 POST "/api/v1/reviews/$id/decision" @{action='APPROVE';decisionId=$key} '' $h
    $b=Begin-Request 21183 POST "/api/v1/reviews/$id/decision" @{action='APPROVE';decisionId=$key} '' $h
    $r=@((End-Request $a),(End-Request $b))
    Check 'two review processes retry one decision without duplicate finals' (@($r|Where-Object {$_.status -eq 200 -and $_.json.data.decisionId -eq $key}).Count -eq 2)
    Wait-Until 'two notification consumers materialize one decision' {[int](DbQuery "SELECT COUNT(*) FROM notifications WHERE decision_id='$key'")[0] -eq 1} 90
    $payload=(DbQuery "SELECT payload FROM event_outbox WHERE event_type='ArticleStatusChangedEvent' AND JSON_UNQUOTE(JSON_EXTRACT(payload,'$.decisionId'))='$key' LIMIT 1")[0]
    Publish-Replay 'article.status.changed.exchange' $payload
    $copy=$payload|ConvertFrom-Json;$copy.eventId=[guid]::NewGuid().ToString();Publish-Replay 'article.status.changed.exchange' ($copy|ConvertTo-Json -Depth 12 -Compress)
    Stop-Instance 'notification-service-a'
    Publish-Replay 'article.status.changed.exchange' ($copy|ConvertTo-Json -Depth 12 -Compress)
    Start-Sleep -Seconds 2
    Check 'duplicate and consumer failover preserve one log and two delivery intents' ([int](DbQuery "SELECT COUNT(*) FROM review_logs WHERE decision_id='$key'")[0] -eq 1 -and [int](DbQuery "SELECT COUNT(*) FROM notification_deliveries d JOIN notifications n ON n.id=d.notification_id WHERE n.decision_id='$key'")[0] -eq 2)
    Start-Instance 'notification-service-a' 'notification-service' 21086
    Wait-Until 'restarted notification consumer rejoins competing queue' {
        $q=Invoke-RestMethod "http://127.0.0.1:15683/api/queues/$namespace" -Headers $script:rabbitHeaders
        @($q|Where-Object {$_.name -eq 'article.status.changed.notification' -and $_.consumers -ge 2}).Count -eq 1
    } 30
    $queues=Invoke-RestMethod "http://127.0.0.1:15683/api/queues/$namespace" -Headers $script:rabbitHeaders
    $queues|Select-Object name,consumers,messages,messages_ready,messages_unacknowledged|ConvertTo-Json|Set-Content (Join-Path $run 'queue-state.json')
    Check 'real broker reports multi-process competing consumers' (@($queues|Where-Object {$_.name -match 'notification' -and $_.consumers -ge 2}).Count -gt 0)
}
. (Join-Path $PSScriptRoot 's5-recovery-faults.ps1')
