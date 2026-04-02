param(
    [string]$GatewayBaseUrl = "https://api.example.com",
    [string]$AdminAccount,
    [string]$AdminPassword,
    [int]$TimeoutSeconds = 120,
    [string]$TraceId = ""
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
[Console]::InputEncoding = [System.Text.UTF8Encoding]::new($false)
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)
$OutputEncoding = [System.Text.UTF8Encoding]::new($false)

if ([string]::IsNullOrWhiteSpace($AdminAccount) -or [string]::IsNullOrWhiteSpace($AdminPassword)) {
    throw "AdminAccount and AdminPassword are required"
}

if ([string]::IsNullOrWhiteSpace($TraceId)) {
    $TraceId = "sae-smoke-" + [guid]::NewGuid().ToString("N")
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
        TimeoutSec = 20
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
        } else {
            $null
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

function Assert-ResultCode {
    param(
        $Response,
        [string]$Context
    )

    if ($null -eq $Response.Json -or $Response.Json.code -ne 200) {
        throw "$Context returned unexpected result: $($Response.Body)"
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

$commonHeaders = @{ "X-Trace-Id" = $TraceId }

$home = Invoke-Api -Method Get -Uri "$GatewayBaseUrl/api/v1/home" -Headers $commonHeaders
Assert-StatusCode -Response $home -ExpectedStatus 200 -Context "Public home"

$invalidToken = Invoke-Api -Method Get -Uri "$GatewayBaseUrl/api/v1/articles/drafts" -Headers @{
    "X-Trace-Id" = $TraceId
    "Authorization" = "Bearer invalid-token"
}
Assert-StatusCode -Response $invalidToken -ExpectedStatus 401 -Context "Invalid token"

$suffix = Get-Date -Format "MMddHHmmss"
$authorUsername = "sae_author_$suffix"
$authorEmail = "$authorUsername@example.com"
$password = "Passw0rd!123"
$articleTitle = "SAE Smoke $suffix"
$articleContent = ("This is a smoke article for SAE validation. " * 4) + "trace=$TraceId"

$authorRegister = Invoke-Api -Method Post -Uri "$GatewayBaseUrl/api/v1/auth/register" -Headers $commonHeaders -Body @{
    username = $authorUsername
    email = $authorEmail
    password = $password
}
Assert-StatusCode -Response $authorRegister -ExpectedStatus 200 -Context "Author register"
Assert-ResultCode -Response $authorRegister -Context "Author register"

$authorLogin = Invoke-Api -Method Post -Uri "$GatewayBaseUrl/api/v1/auth/login" -Headers $commonHeaders -Body @{
    account = $authorUsername
    password = $password
    rememberMe = $false
}
Assert-StatusCode -Response $authorLogin -ExpectedStatus 200 -Context "Author login"
Assert-ResultCode -Response $authorLogin -Context "Author login"
$authorToken = [string]$authorLogin.Json.data.token

$adminLogin = Invoke-Api -Method Post -Uri "$GatewayBaseUrl/api/v1/auth/login" -Headers $commonHeaders -Body @{
    account = $AdminAccount
    password = $AdminPassword
    rememberMe = $false
}
Assert-StatusCode -Response $adminLogin -ExpectedStatus 200 -Context "Admin login"
Assert-ResultCode -Response $adminLogin -Context "Admin login"
$adminToken = [string]$adminLogin.Json.data.token

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
Assert-StatusCode -Response $submit -ExpectedStatus 200 -Context "Submit article"
Assert-ResultCode -Response $submit -Context "Submit article"

$review = Invoke-Api -Method Post -Uri "$GatewayBaseUrl/api/v1/reviews/$articleId/action" -Headers @{
    "X-Trace-Id" = $TraceId
    "Authorization" = "Bearer $adminToken"
} -Body @{
    action = "APPROVE"
    reason = $null
}
Assert-StatusCode -Response $review -ExpectedStatus 200 -Context "Approve review"
Assert-ResultCode -Response $review -Context "Approve review"

$pngBytes = [Convert]::FromBase64String("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jXioAAAAASUVORK5CYII=")
$tempFile = [System.IO.Path]::GetTempFileName()
[System.IO.File]::WriteAllBytes($tempFile, $pngBytes)
try {
    $uploadResponse = Invoke-WebRequest -Uri "$GatewayBaseUrl/api/v1/uploads/images?bizType=ARTICLE_IMAGE&articleId=$articleId" `
        -Method Post `
        -Headers @{
            "X-Trace-Id" = $TraceId
            "Authorization" = "Bearer $authorToken"
        } `
        -Form @{ file = Get-Item $tempFile } `
        -TimeoutSec 20

    $uploadJson = $uploadResponse.Content | ConvertFrom-Json
    if ($uploadJson.code -ne 200 -or [string]::IsNullOrWhiteSpace($uploadJson.data.url)) {
        throw "Upload validation failed: $($uploadResponse.Content)"
    }
}
finally {
    Remove-Item $tempFile -Force -ErrorAction SilentlyContinue
}

Wait-Until -Description "search visibility" -TimeoutSeconds $TimeoutSeconds -Condition {
    $search = Invoke-Api -Method Get -Uri "$GatewayBaseUrl/api/v1/search/articles?keyword=$([uri]::EscapeDataString($articleTitle))&page=1&pageSize=10" -Headers $commonHeaders
    if ($search.StatusCode -ne 200 -or $null -eq $search.Json -or $search.Json.code -ne 200) {
        return $false
    }
    return @($search.Json.data.list | Where-Object { $_.articleId -eq $articleId }).Count -gt 0
}

Write-Host "SAE smoke passed" -ForegroundColor Green
Write-Host "TraceId: $TraceId" -ForegroundColor Green
Write-Host "ArticleId: $articleId" -ForegroundColor Green
