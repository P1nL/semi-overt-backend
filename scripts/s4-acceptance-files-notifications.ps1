# Dot-sourced by the isolated harness. Writes only synthetic fixtures.
function Upload([byte[]]$Bytes,[string]$Name,[string]$Mime,[string]$Token,[string]$Biz='AVATAR',[string]$OldUrl='') {
    $request=[Net.Http.HttpRequestMessage]::new([Net.Http.HttpMethod]::Post,'http://127.0.0.1:20080/api/v1/upload')
    $multipart=[Net.Http.MultipartFormDataContent]::new()
    $file=[Net.Http.ByteArrayContent]::new($Bytes);$file.Headers.ContentType=[Net.Http.Headers.MediaTypeHeaderValue]::new($Mime)
    $multipart.Add($file,'file',$Name);$multipart.Add([Net.Http.StringContent]::new($Biz),'bizType')
    if($OldUrl){$multipart.Add([Net.Http.StringContent]::new($OldUrl),'oldUrl')}
    $request.Content=$multipart
    if($Token){$request.Headers.Authorization=[Net.Http.Headers.AuthenticationHeaderValue]::new('Bearer',$Token)}
    return Finish-Api @{request=$request;task=$script:http.SendAsync($request);path='/api/v1/upload'}
}
function Run-PackageThree($Tokens) {
    [byte[]]$png=[Convert]::FromBase64String('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jBz0AAAAASUVORK5CYII=')
    Check 'anonymous multipart upload denied' ((Upload $png 'pixel.png' 'image/png' '').status -eq 401)
    $good=Upload $png 'pixel.png' 'image/png' $Tokens.s4writer
    Check 'decoded local image upload returns actual metadata' ($good.status -eq 200 -and $good.json.data.width -eq 1 -and $good.json.data.height -eq 1 -and $good.json.data.size -gt 0 -and $good.json.data.url.StartsWith('/static/uploads/'))
    $fetch=Invoke-WebRequest ('http://127.0.0.1:20080'+$good.json.data.url) -SkipHttpErrorCheck
    Check 'local uploaded file is retrievable via gateway' ([int]$fetch.StatusCode -eq 200 -and $fetch.Headers['Content-Type'] -match 'image/png')
    $fake=Upload ([Text.Encoding]::UTF8.GetBytes('<svg onload="alert(1)"/>')) 'fake.png' 'image/png' $Tokens.s4writer
    Check 'declared MIME cannot disguise non-raster bytes' ($fake.status -eq 400)
    $empty=Upload ([byte[]]@()) 'empty.png' 'image/png' $Tokens.s4writer
    Check 'empty image rejected' ($empty.status -eq 400)
    $replacement=Upload $png 'new.png' 'image/png' $Tokens.s4visitor 'AVATAR' $good.json.data.url
    Check 'another user cannot delete an image through oldUrl' ($replacement.status -in @(200,400,403) -and [int](Invoke-WebRequest ('http://127.0.0.1:20080'+$good.json.data.url) -SkipHttpErrorCheck).StatusCode -eq 200)
    $traversal=Invoke-WebRequest 'http://127.0.0.1:20085/static/uploads/%2e%2e/%2e%2e/pom.xml' -SkipHttpErrorCheck
    Check 'static traversal never serves repository files' ([int]$traversal.StatusCode -in @(400,401,403,404))
    Check 'uploads consume durable user quota' ([int](DbQuery "SELECT COUNT(*) FROM rate_limit_buckets WHERE bucket_key LIKE 'upload:day:user:%' AND request_count>0")[0] -gt 0)
    $good.json | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $run 'package3-wire.json') -Encoding utf8
}
function Run-PackageFour($Tokens) {
    foreach($prefix in @('/api','/api/v1')) {
        Check "$prefix notifications require session" ((Api GET "$prefix/notifications").status -eq 401)
        $history=Api GET "$prefix/notifications?limit=20" $null $Tokens.s4writer
        Check "$prefix historical notification survives unknown relation" ($history.status -eq 200 -and @($history.json.data).Count -eq 1 -and $history.json.data[0].id -eq 9001 -and $history.json.data[0].content -eq '未知关联历史原文')
        $visitor=Api GET "$prefix/notifications?limit=20&userId=101" $null $Tokens.s4visitor @{'X-User-Id'='101'}
        Check "$prefix notification owner cannot be forged by query/header" ($visitor.status -eq 200 -and @($visitor.json.data).Count -eq 1 -and $visitor.json.data[0].id -eq 9002)
    }
    $id=New-Draft $Tokens.s4writer
    $body='用于通知事件权威验证的真实提审正文。'*12
    Check 'notification fixture saved' ((Api PUT "/api/v1/articles/$id/draft" @{title='S4 review notification';content=$body;version=0} $Tokens.s4writer).status -eq 200)
    Check 'notification fixture submitted' ((Api POST "/api/v1/articles/$id/submit" $null $Tokens.s4writer).status -eq 200)
    Wait-Until 'notification fixture assigned' {[int](DbQuery "SELECT COUNT(*) FROM review_tasks WHERE article_id=$id AND assigned_admin_id IS NOT NULL")[0] -eq 1}
    $assigned=[int](DbQuery "SELECT assigned_admin_id FROM review_tasks WHERE article_id=$id")[0]
    $admin=if($assigned -eq 103){$Tokens.s4admin}else{$Tokens.s4otheradmin}
    $key=[guid]::NewGuid().ToString()
    $decision=Api POST "/api/v1/review/$id/decision" @{action='APPROVE';decisionId=$key} $admin
    Check 'review returns content-confirmed final' ($decision.status -eq 200 -and $decision.json.data.status -eq 'APPROVED')
    Wait-Until 'notification projects authority decision' {[int](DbQuery "SELECT COUNT(*) FROM notifications WHERE decision_id='$key'")[0] -eq 1}
    $payload=(DbQuery "SELECT payload FROM event_outbox WHERE aggregate_id='$id' AND event_type='ArticleStatusChangedEvent' AND JSON_UNQUOTE(JSON_EXTRACT(payload,'$.decisionId'))='$key' LIMIT 1")[0]
    Publish-Replay 'article.status.changed.exchange' $payload
    $changed=$payload|ConvertFrom-Json;$changed.eventId=[guid]::NewGuid().ToString()
    Publish-Replay 'article.status.changed.exchange' ($changed|ConvertTo-Json -Depth 12 -Compress)
    Start-Sleep -Seconds 2
    Check 'same decision across replay event IDs remains one notification' ([int](DbQuery "SELECT COUNT(*) FROM notifications WHERE decision_id='$key'")[0] -eq 1)
    $list=Api GET '/api/v1/notifications?limit=1' $null $Tokens.s4writer
    Check 'notification limit/sort returns newest source-compatible item' ($list.status -eq 200 -and @($list.json.data).Count -eq 1 -and $list.json.data[0].id -ne 9001 -and $list.json.data[0].type -eq 'ARTICLE_REVIEW' -and $list.json.data[0].title -eq '文章审核结果')
    Check 'pending email bookkeeping is not reported as sent' ([int](DbQuery "SELECT COUNT(*) FROM notification_deliveries d JOIN notifications n ON n.id=d.notification_id WHERE n.decision_id='$key' AND d.channel='EMAIL' AND d.status='PENDING' AND d.sent_at IS NULL")[0] -eq 1)
    Check 'history not assigned fabricated decision identity' ([int](DbQuery 'SELECT COUNT(*) FROM notifications WHERE id=9001 AND decision_id IS NULL AND biz_id IS NULL')[0] -eq 1)
    $list.json|ConvertTo-Json -Depth 10|Set-Content -LiteralPath (Join-Path $run 'package4-wire.json') -Encoding utf8
}
