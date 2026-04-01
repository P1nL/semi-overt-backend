param(
    [string]$GatewayBaseUrl = "http://127.0.0.1:8080",
    [string]$ServiceHost = "127.0.0.1",
    [int]$TimeoutSeconds = 120,
    [string]$TraceId = "",
    [switch]$SkipE2E
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
[Console]::InputEncoding = [System.Text.UTF8Encoding]::new($false)
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)
$OutputEncoding = [System.Text.UTF8Encoding]::new($false)

if ([string]::IsNullOrWhiteSpace($TraceId)) {
    $TraceId = "smoke-" + [guid]::NewGuid().ToString("N")
}

$repoRoot = Split-Path -Parent $PSScriptRoot
$runtimeLogRoot = Join-Path $repoRoot ".codex-runtime\logs"

$servicePorts = @{
    "gateway-service" = 8080
    "auth-service" = 8081
    "content-service" = 8082
    "review-service" = 8083
    "search-service" = 8084
    "file-service" = 8085
    "notification-service" = 8086
}

$mysqlRootPassword = if (-not [string]::IsNullOrWhiteSpace($env:MYSQL_ROOT_PASSWORD)) {
    $env:MYSQL_ROOT_PASSWORD
}
else {
    "1234"
}

$mysqlDatabase = if (-not [string]::IsNullOrWhiteSpace($env:MYSQL_DATABASE)) {
    $env:MYSQL_DATABASE
}
else {
    "content_platform"
}

$rabbitMqUser = if (-not [string]::IsNullOrWhiteSpace($env:RABBITMQ_DEFAULT_USER)) {
    $env:RABBITMQ_DEFAULT_USER
}
elseif (-not [string]::IsNullOrWhiteSpace($env:RABBITMQ_USERNAME)) {
    $env:RABBITMQ_USERNAME
}
else {
    "guest"
}

$rabbitMqPassword = if (-not [string]::IsNullOrWhiteSpace($env:RABBITMQ_DEFAULT_PASS)) {
    $env:RABBITMQ_DEFAULT_PASS
}
elseif (-not [string]::IsNullOrWhiteSpace($env:RABBITMQ_PASSWORD)) {
    $env:RABBITMQ_PASSWORD
}
else {
    "guest"
}

function Write-Step {
    param([string]$Message)
    Write-Host "==> $Message" -ForegroundColor Cyan
}

function Invoke-Api {
    param(
        [string]$Method,
        [string]$Uri,
        $Body = $null,
        [hashtable]$Headers = @{}
    )

    $params = @{
        Uri = $Uri
        Method = $Method
        Headers = $Headers
        TimeoutSec = 15
        ErrorAction = "Stop"
    }

    if ($null -ne $Body) {
        $params.ContentType = "application/json"
        $params.Body = ($Body | ConvertTo-Json -Depth 10 -Compress)
    }

    try {
        $response = Invoke-WebRequest @params
        $statusCode = [int]$response.StatusCode
        $rawBody = $response.Content
    }
    catch {
        if ($_.Exception.Response -eq $null) {
            throw
        }

        $statusCode = [int]$_.Exception.Response.StatusCode
        $rawBody = if ($null -ne $_.ErrorDetails -and $null -ne $_.ErrorDetails.PSObject.Properties["Message"]) {
            $_.ErrorDetails.Message
        }
        else {
            $null
        }
        if ([string]::IsNullOrWhiteSpace($rawBody)) {
            $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
            $rawBody = $reader.ReadToEnd()
            $reader.Dispose()
        }
    }

    $json = $null
    if (-not [string]::IsNullOrWhiteSpace($rawBody)) {
        try {
            $json = $rawBody | ConvertFrom-Json
        }
        catch {
            $json = $null
        }
    }

    return [pscustomobject]@{
        StatusCode = $statusCode
        Body = $rawBody
        Json = $json
    }
}

function Assert-StatusCode {
    param(
        $Response,
        [int]$ExpectedStatus,
        [string]$Context
    )

    if ($Response.StatusCode -ne $ExpectedStatus) {
        throw "$Context failed. Expected HTTP $ExpectedStatus, got $($Response.StatusCode). Body: $($Response.Body)"
    }
}

function Wait-Until {
    param(
        [scriptblock]$Condition,
        [string]$Description,
        [int]$TimeoutSeconds = 60,
        [int]$SleepSeconds = 2
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        if (& $Condition) {
            return
        }
        Start-Sleep -Seconds $SleepSeconds
    }

    throw "Timeout waiting for $Description"
}

function Invoke-DockerCompose {
    param([string[]]$Arguments)

    Push-Location $repoRoot
    try {
        $output = & docker compose @Arguments 2>&1
        if ($LASTEXITCODE -ne 0) {
            throw "docker compose $($Arguments -join ' ') failed: $($output -join [Environment]::NewLine)"
        }
        return $output
    }
    finally {
        Pop-Location
    }
}

function Test-InfrastructureHealth {
    Write-Step "Checking Docker container health"
    foreach ($service in @("mysql", "redis", "nacos", "rabbitmq")) {
        $containerId = (Invoke-DockerCompose -Arguments @("ps", "-q", $service) | Select-Object -Last 1).Trim()
        if ([string]::IsNullOrWhiteSpace($containerId)) {
            throw "Infrastructure service $service is not running"
        }

        $health = (& docker inspect --format "{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}" $containerId).Trim()
        if ($health -ne "healthy" -and $health -ne "running") {
            throw "Infrastructure service $service is $health"
        }
    }

    Write-Step "Checking MySQL schema"
    $mysqlResult = Invoke-DockerCompose -Arguments @(
        "exec", "-T", "-e", "MYSQL_PWD=$mysqlRootPassword", "mysql",
        "mysql", "-uroot", "-Nse", "SHOW DATABASES LIKE '$mysqlDatabase';"
    )
    if (($mysqlResult -join "").Trim() -ne $mysqlDatabase) {
        throw "MySQL schema check failed: $($mysqlResult -join [Environment]::NewLine)"
    }

    Write-Step "Checking Redis PING"
    $redisResult = Invoke-DockerCompose -Arguments @("exec", "-T", "redis", "redis-cli", "ping")
    if (($redisResult -join "").Trim() -ne "PONG") {
        throw "Redis PING failed: $($redisResult -join [Environment]::NewLine)"
    }

    Write-Step "Checking Nacos health endpoint"
    $nacos = Invoke-Api -Method Get -Uri "http://127.0.0.1:8848/nacos/actuator/health"
    Assert-StatusCode -Response $nacos -ExpectedStatus 200 -Context "Nacos health"

    Write-Step "Checking RabbitMQ management API"
    $rabbitHeaders = @{
        Authorization = "Basic " + [Convert]::ToBase64String([System.Text.Encoding]::ASCII.GetBytes("$rabbitMqUser`:$rabbitMqPassword"))
    }
    $rabbit = Invoke-Api -Method Get -Uri "http://127.0.0.1:15672/api/overview" -Headers $rabbitHeaders
    Assert-StatusCode -Response $rabbit -ExpectedStatus 200 -Context "RabbitMQ management API"
}

function Test-ServiceEndpoints {
    Write-Step "Checking service health and info endpoints"
    foreach ($service in $servicePorts.Keys) {
        $port = $servicePorts[$service]
        $health = Invoke-Api -Method Get -Uri "http://$ServiceHost`:$port/actuator/health"
        Assert-StatusCode -Response $health -ExpectedStatus 200 -Context "$service actuator health"
        $healthStatus = if ($null -ne $health.Json -and $null -ne $health.Json.PSObject.Properties["status"]) {
            $health.Json.status
        }
        else {
            $null
        }
        if ($null -ne $healthStatus -and $healthStatus -ne "UP") {
            throw "$service /actuator/health returned non-UP status: $($health.Body)"
        }

        $info = Invoke-Api -Method Get -Uri "http://$ServiceHost`:$port/actuator/info"
        Assert-StatusCode -Response $info -ExpectedStatus 200 -Context "$service actuator info"
    }

    $homeResponse = Invoke-Api -Method Get -Uri "$GatewayBaseUrl/api/v1/home" -Headers @{ "X-Trace-Id" = $TraceId }
    Assert-StatusCode -Response $homeResponse -ExpectedStatus 200 -Context "Gateway public home endpoint"

    $invalidToken = Invoke-Api -Method Get -Uri "$GatewayBaseUrl/api/v1/articles/drafts" -Headers @{
        "X-Trace-Id" = $TraceId
        "Authorization" = "Bearer invalid-token"
    }
    Assert-StatusCode -Response $invalidToken -ExpectedStatus 401 -Context "Gateway invalid token protection"
}

function Invoke-MySqlScalar {
    param([string]$Query)

    $result = Invoke-DockerCompose -Arguments @(
        "exec", "-T", "-e", "MYSQL_PWD=$mysqlRootPassword", "mysql",
        "mysql", "-uroot", "-D", $mysqlDatabase, "-Nse", $Query
    )
    return ($result -join "").Trim()
}

function Assert-ResultCode {
    param(
        $Response,
        [string]$Context
    )

    if ($null -eq $Response.Json) {
        throw "$Context returned non-JSON body: $($Response.Body)"
    }

    if ($Response.Json.code -ne 200) {
        throw "$Context returned business code $($Response.Json.code): $($Response.Body)"
    }
}

function Invoke-E2ESmoke {
    Write-Step "Running gateway E2E smoke flow"

    $suffix = Get-Date -Format "MMddHHmmss"
    $authorUsername = "author_$suffix"
    $adminUsername = "review_$suffix"
    $authorEmail = "$authorUsername@example.com"
    $adminEmail = "$adminUsername@example.com"
    $password = "Passw0rd!123"
    $articleTitle = "Smoke Article $suffix"
    $articleContent = ("This is a distributed demo article for smoke validation. " * 4) + "trace=$TraceId"

    $commonHeaders = @{ "X-Trace-Id" = $TraceId }

    $authorRegister = Invoke-Api -Method Post -Uri "$GatewayBaseUrl/api/v1/auth/register" -Headers $commonHeaders -Body @{
        username = $authorUsername
        email = $authorEmail
        password = $password
    }
    Assert-StatusCode -Response $authorRegister -ExpectedStatus 200 -Context "Author register"
    Assert-ResultCode -Response $authorRegister -Context "Author register"
    $authorId = [int64]$authorRegister.Json.data.userId

    $adminRegister = Invoke-Api -Method Post -Uri "$GatewayBaseUrl/api/v1/auth/register" -Headers $commonHeaders -Body @{
        username = $adminUsername
        email = $adminEmail
        password = $password
    }
    Assert-StatusCode -Response $adminRegister -ExpectedStatus 200 -Context "Reviewer register"
    Assert-ResultCode -Response $adminRegister -Context "Reviewer register"

    [void](Invoke-MySqlScalar -Query "UPDATE users SET role = 'ADMIN' WHERE username = '$adminUsername'; SELECT ROW_COUNT();")

    $authorLogin = Invoke-Api -Method Post -Uri "$GatewayBaseUrl/api/v1/auth/login" -Headers $commonHeaders -Body @{
        account = $authorUsername
        password = $password
        rememberMe = $false
    }
    Assert-StatusCode -Response $authorLogin -ExpectedStatus 200 -Context "Author login"
    Assert-ResultCode -Response $authorLogin -Context "Author login"
    $authorToken = [string]$authorLogin.Json.data.token

    $adminLogin = Invoke-Api -Method Post -Uri "$GatewayBaseUrl/api/v1/auth/login" -Headers $commonHeaders -Body @{
        account = $adminUsername
        password = $password
        rememberMe = $false
    }
    Assert-StatusCode -Response $adminLogin -ExpectedStatus 200 -Context "Reviewer login"
    Assert-ResultCode -Response $adminLogin -Context "Reviewer login"
    $adminToken = [string]$adminLogin.Json.data.token

    $authorDraftList = Invoke-Api -Method Get -Uri "$GatewayBaseUrl/api/v1/articles/drafts" -Headers @{
        "X-Trace-Id" = $TraceId
        "Authorization" = "Bearer $authorToken"
    }
    Assert-StatusCode -Response $authorDraftList -ExpectedStatus 200 -Context "Author draft list with valid token"
    Assert-ResultCode -Response $authorDraftList -Context "Author draft list with valid token"

    $createArticle = Invoke-Api -Method Post -Uri "$GatewayBaseUrl/api/v1/articles" -Headers @{
        "X-Trace-Id" = $TraceId
        "Authorization" = "Bearer $authorToken"
    }
    Assert-StatusCode -Response $createArticle -ExpectedStatus 200 -Context "Create article"
    Assert-ResultCode -Response $createArticle -Context "Create article"
    $articleId = [int64]$createArticle.Json.data.id

    $saveDraft = Invoke-Api -Method Put -Uri "$GatewayBaseUrl/api/v1/articles/$articleId/draft" -Headers @{
        "X-Trace-Id" = $TraceId
        "Authorization" = "Bearer $authorToken"
    } -Body @{
        title = $articleTitle
        content = $articleContent
        summary = "Smoke summary $suffix"
        coverUrl = ""
        coverColor = "#123456"
        clientWordCount = 128
    }
    Assert-StatusCode -Response $saveDraft -ExpectedStatus 200 -Context "Save draft"
    Assert-ResultCode -Response $saveDraft -Context "Save draft"

    $submit = Invoke-Api -Method Post -Uri "$GatewayBaseUrl/api/v1/articles/$articleId/submit" -Headers @{
        "X-Trace-Id" = $TraceId
        "Authorization" = "Bearer $authorToken"
    }
    Assert-StatusCode -Response $submit -ExpectedStatus 200 -Context "Submit article for review"
    Assert-ResultCode -Response $submit -Context "Submit article for review"

    $review = Invoke-Api -Method Post -Uri "$GatewayBaseUrl/api/v1/reviews/$articleId/action" -Headers @{
        "X-Trace-Id" = $TraceId
        "Authorization" = "Bearer $adminToken"
    } -Body @{
        action = "APPROVE"
        reason = $null
    }
    Assert-StatusCode -Response $review -ExpectedStatus 200 -Context "Approve review"
    Assert-ResultCode -Response $review -Context "Approve review"

    Wait-Until -Description "notification rows" -TimeoutSeconds $TimeoutSeconds -Condition {
        [int](Invoke-MySqlScalar -Query "SELECT COUNT(*) FROM notifications WHERE user_id = $authorId AND biz_id = $articleId;") -ge 1
    }

    Wait-Until -Description "notification deliveries" -TimeoutSeconds $TimeoutSeconds -Condition {
        $inApp = [int](Invoke-MySqlScalar -Query @"
SELECT COUNT(*)
FROM notification_deliveries d
JOIN notifications n ON n.id = d.notification_id
WHERE n.user_id = $authorId
  AND n.biz_id = $articleId
  AND d.channel = 'IN_APP';
"@)
        $email = [int](Invoke-MySqlScalar -Query @"
SELECT COUNT(*)
FROM notification_deliveries d
JOIN notifications n ON n.id = d.notification_id
WHERE n.user_id = $authorId
  AND n.biz_id = $articleId
  AND d.channel = 'EMAIL';
"@)
        return $inApp -ge 1 -and $email -ge 1
    }

    Wait-Until -Description "search visibility" -TimeoutSeconds $TimeoutSeconds -Condition {
        $search = Invoke-Api -Method Get -Uri "$GatewayBaseUrl/api/v1/search/articles?keyword=$([uri]::EscapeDataString($articleTitle))&page=1&pageSize=10" -Headers $commonHeaders
        if ($search.StatusCode -ne 200 -or $null -eq $search.Json -or $search.Json.code -ne 200) {
            return $false
        }
        $list = @($search.Json.data.list)
        return @($list | Where-Object { $_.articleId -eq $articleId }).Count -gt 0
    }

    Wait-Until -Description "traceId in service logs" -TimeoutSeconds 30 -Condition {
        if (-not (Test-Path $runtimeLogRoot)) {
            return $false
        }

        $match = Get-ChildItem -Path $runtimeLogRoot -Filter "*.log" -File -ErrorAction SilentlyContinue |
            Select-String -Pattern $TraceId -SimpleMatch -ErrorAction SilentlyContinue |
            Select-Object -First 1
        return $null -ne $match
    }

    $rabbitHeaders = @{
        Authorization = "Basic " + [Convert]::ToBase64String([System.Text.Encoding]::ASCII.GetBytes("$rabbitMqUser`:$rabbitMqPassword"))
    }
    $queues = Invoke-Api -Method Get -Uri "http://127.0.0.1:15672/api/queues/%2F" -Headers $rabbitHeaders
    Assert-StatusCode -Response $queues -ExpectedStatus 200 -Context "RabbitMQ queue listing"

    $requiredQueues = @(
        "article.submitted.review",
        "review.decided.content",
        "article.status.changed.notification",
        "article.status.changed.search"
    )
    $queueNames = @($queues.Json | ForEach-Object { $_.name })
    foreach ($queueName in $requiredQueues) {
        if ($queueNames -notcontains $queueName) {
            throw "Expected RabbitMQ queue $queueName to exist"
        }
    }

    $notificationCount = Invoke-MySqlScalar -Query "SELECT COUNT(*) FROM notifications WHERE user_id = $authorId AND biz_id = $articleId;"
    $deliveryCount = Invoke-MySqlScalar -Query @"
SELECT COUNT(*)
FROM notification_deliveries d
JOIN notifications n ON n.id = d.notification_id
WHERE n.user_id = $authorId
  AND n.biz_id = $articleId;
"@

    Write-Host "E2E smoke passed" -ForegroundColor Green
    Write-Host "TraceId: $TraceId" -ForegroundColor Green
    Write-Host "ArticleId: $articleId" -ForegroundColor Green
    Write-Host "Notifications: $notificationCount" -ForegroundColor Green
    Write-Host "Deliveries: $deliveryCount" -ForegroundColor Green
}

Write-Step "TraceId: $TraceId"
Test-InfrastructureHealth
Test-ServiceEndpoints

if ($SkipE2E) {
    Write-Host "Smoke checks passed (infrastructure + actuator + gateway semantics)." -ForegroundColor Green
    exit 0
}

Invoke-E2ESmoke
