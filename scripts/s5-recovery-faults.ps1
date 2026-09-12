function Run-AuthorityRecovery([string]$Token){
    $id=Seed-Review $Token
    Wait-Until 'authority-fault fixture assigned' {[int](DbQuery "SELECT COUNT(*) FROM review_tasks WHERE article_id=$id AND assigned_admin_id IS NOT NULL")[0] -eq 1} 90
    $assigned=[int](DbQuery "SELECT assigned_admin_id FROM review_tasks WHERE article_id=$id")[0];$admin=$tokens[$assigned];$key=[guid]::NewGuid().ToString()
    Stop-Instance 'content-service-a';Stop-Instance 'content-service-b'
    try{
        $r=Request 21080 POST "/api/v1/review/$id/decision" @{action='APPROVE';decisionId=$key} $admin
        Check 'all content authorities down never returns a final success' ($r.status -eq 503 -and $r.ms -lt 10000)
        Check 'unknown decision survives in review command database' ([int](DbQuery "SELECT COUNT(*) FROM review_commands WHERE decision_id='$key' AND state='PROCESSING'")[0] -eq 1)
        Check 'content has not accepted a result while offline' ([int](DbQuery "SELECT COUNT(*) FROM content_review_decisions WHERE decision_id='$key' AND state='FINAL'")[0] -eq 0)
    }finally{
        Start-Instance 'content-service-a' 'content-service' 21082
        Start-Instance 'content-service-b' 'content-service' 21182
    }
    $timer=[Diagnostics.Stopwatch]::StartNew()
    Wait-Until 'same persisted decision recovers through other gateway' {$r=Request 21180 GET "/api/v1/review/$id/decision-status?decisionId=$key" $null $admin; $r.status -eq 200 -and $r.json.data.state -eq 'FINAL'} 90
    $timer.Stop();@{scenario='content';recoveryMs=$timer.ElapsedMilliseconds}|ConvertTo-Json -Compress|Add-Content (Join-Path $run 'recovery-times.jsonl')
    Wait-Until 'recovered decision has exactly one final log and notification' {[int](DbQuery "SELECT (SELECT COUNT(*) FROM content_review_decisions WHERE decision_id='$key' AND state='FINAL')+(SELECT COUNT(*) FROM review_logs WHERE decision_id='$key')+(SELECT COUNT(*) FROM notifications WHERE decision_id='$key')")[0] -eq 3} 90
}
function Run-DatabaseRecovery($Session){
    $id=New-Draft $Session.token
    $before=[int](DbQuery 'SELECT COUNT(*) FROM articles')[0]
    Fault mysql cut
    try{
        foreach($port in @(21080,21180)){
            $r=Request $port GET '/api/v1/users/me' $null $Session.token
            Check "gateway $port DB outage yields bounded service failure not logout" ($r.status -ge 500 -and $r.status -le 599 -and $r.ms -lt 10000)
        }
        $r=Request 21181 POST '/api/v1/auth/refresh' $null '' @{Cookie=$Session.cookie}
        Check 'DB outage does not revoke refresh cookie' ($r.status -ge 500 -and $r.ms -lt 10000 -and @($r.cookies|Where-Object {$_ -match 'Max-Age=0'}).Count -eq 0)
        $r=Request 21082 PUT "/api/v1/articles/$id/draft" @{title='must not persist';version=0} '' (InternalHeaders)
        Check 'content DB outage fails instead of acknowledging write' ($r.status -ge 500 -and $r.ms -lt 10000)
        $r=Request 21081 POST '/internal/auth/budget/consume' @{clientIp='192.0.2.66';operation='SEARCH'} '' @{'X-Internal-Token'=$env:INTERNAL_TOKEN}
        Check 'budget DB outage fails closed' ($r.status -ge 500 -and $r.ms -lt 10000)
    }finally{Fault mysql restore}
    $timer=[Diagnostics.Stopwatch]::StartNew()
    Wait-Until 'both auth instances recover the original session' { (Validate 21081 $Session.token).status -eq 200 -and (Validate 21181 $Session.token).status -eq 200 } 90
    $timer.Stop();@{scenario='mysql';recoveryMs=$timer.ElapsedMilliseconds}|ConvertTo-Json -Compress|Add-Content (Join-Path $run 'recovery-times.jsonl')
    Check 'failed DB write left article version unchanged' ([int](DbQuery "SELECT version FROM articles WHERE id=$id")[0] -eq 0 -and [int](DbQuery 'SELECT COUNT(*) FROM articles')[0] -eq $before)
    $r=Request 21182 PUT "/api/v1/articles/$id/draft" @{title='recovered write';version=0} '' (InternalHeaders)
    Check 'same baseline saves after database reconnect' ($r.status -eq 200 -and [int](DbQuery "SELECT version FROM articles WHERE id=$id")[0] -eq 1)
    Stop-Instance 'auth-service-a'
    try{
        Check 'other auth instance keeps durable session alive during rolling restart' ((Validate 21181 $Session.token).status -eq 200)
    }finally{Start-Instance 'auth-service-a' 'auth-service' 21081}
    Check 'restarted auth process observes original durable session' ((Validate 21081 $Session.token).status -eq 200)
}
function Run-RedisRecovery([string]$Token){
    $id=New-Draft $Token
    $label=& docker inspect --format '{{index .Config.Labels "semi-overt.s4.owner"}}' $redisContainer
    if($label -ne $db){throw 'Redis ownership mismatch'}
    $beforePort=& docker inspect --format '{{(index (index .NetworkSettings.Ports "6379/tcp") 0).HostPort}}' $redisContainer
    & docker stop $redisContainer|Out-Null;if($LASTEXITCODE -ne 0){throw 'Redis fault failed'}
    try{
        $r=Request 21082 PUT "/api/v1/articles/$id/draft" @{title='database authority during cache outage';version=0} '' (InternalHeaders)
        Check 'content persistence remains independent of Redis' ($r.status -eq 200)
        foreach($port in @(21080,21180)){
            $r=Request $port GET '/api/v1/home'
            Check "gateway $port does not silently bypass Redis limiter on outage" ($r.status -ge 500 -and $r.status -le 599 -and $r.ms -lt 10000)
        }
    }finally{& docker start $redisContainer|Out-Null;if($LASTEXITCODE -ne 0){throw 'Redis restore failed'}}
    $afterPort=& docker inspect --format '{{(index (index .NetworkSettings.Ports "6379/tcp") 0).HostPort}}' $redisContainer
    Check 'Redis restart retains the original network address' ($beforePort -eq $afterPort)
    $timer=[Diagnostics.Stopwatch]::StartNew()
    Wait-Until 'both gateways recover after own Redis restart' {(Request 21080 GET '/api/v1/home').status -eq 200 -and (Request 21180 GET '/api/v1/home').status -eq 200} 90
    $timer.Stop();@{scenario='redis';recoveryMs=$timer.ElapsedMilliseconds}|ConvertTo-Json -Compress|Add-Content (Join-Path $run 'recovery-times.jsonl')
}
function Run-Load {
    foreach($p in @(21080,21180)){1..4|ForEach-Object {$r=Request $p GET '/api/v1/home';if($r.status -ne 200){throw 'Warmup failed'}}}
    $metrics=@()
    for($batch=0;$batch -lt 25;$batch++){
        $pending=@(0..3|ForEach-Object {Begin-Request (21080+100*($_%2)) GET '/api/v1/home'})
        $metrics+=@($pending|ForEach-Object {End-Request $_})
    }
    $latencies=@($metrics.ms|Sort-Object);$p95=$latencies[[int][Math]::Ceiling($latencies.Count*.95)-1]
    $summary=@{requests=100;concurrency=4;success=@($metrics|Where-Object {$_.status -eq 200}).Count;p50Ms=[Math]::Round($latencies[49],2);p95Ms=[Math]::Round($p95,2);maxMs=[Math]::Round($latencies[-1],2);thresholdP95Ms=2000;scope='local regression only, not production capacity'}
    $summary|ConvertTo-Json|Set-Content (Join-Path $run 'load-summary.json')
    Check '100 requests concurrency four both gateways succeed' ($summary.success -eq 100)
    Check 'predeclared local p95 below two seconds' ($p95 -lt 2000)
    Wait-Until 'event backlog converges at end of run' {[int](DbQuery "SELECT COUNT(*) FROM event_outbox WHERE status<>'PUBLISHED'")[0] -eq 0} 90
}
