#requires -Version 7.0
[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$root = 'D:\works\semi-overt-backend'
$docPath = Join-Path $root 'deploy/docker/README.md'
$text = [IO.File]::ReadAllText($docPath)
$lines = [IO.File]::ReadAllLines($docPath)
$blocks = [Collections.Generic.List[object]]::new()
$inside = $false
$language = ''
$start = 0
$content = [Collections.Generic.List[string]]::new()
$tableColumns = $null
for ($i = 0; $i -lt $lines.Count; $i++) {
    $line = $lines[$i]
    if ($line -match '^```(.*)$') {
        if (-not $inside) {
            $inside = $true
            $language = $Matches[1].Trim()
            $start = $i + 2
            $content.Clear()
        } else {
            if ($Matches[1].Trim()) { throw "Unexpected fence suffix at line $($i + 1)" }
            $blocks.Add([pscustomobject]@{Language=$language;Start=$start;Body=($content -join "`n")})
            $inside = $false
        }
        $tableColumns = $null
    } elseif ($inside) {
        $content.Add($line)
    } elseif ($line -match '^\|.*\|$') {
        $columns = $line.Split('|').Count - 2
        if ($null -ne $tableColumns -and $columns -ne $tableColumns) {
            throw "Table column mismatch at line $($i + 1)"
        }
        $tableColumns = $columns
    } else {
        $tableColumns = $null
    }
    if ($line -match '[\t ]+$') { throw "Trailing whitespace at line $($i + 1)" }
}
if ($inside) { throw 'Unclosed code fence' }
if ($text.Contains('handbook:')) { throw 'Unfinished document marker' }
if ($text.Contains([char]0xfffd)) { throw 'Unicode replacement character' }
if ($text -match '\bsk-[A-Za-z0-9_-]{20,}\b') { throw 'Possible real API key in document' }
foreach ($block in $blocks | Where-Object Language -eq 'powershell') {
    $tokens = $null
    $parseErrors = $null
    $null = [Management.Automation.Language.Parser]::ParseInput($block.Body, [ref]$tokens, [ref]$parseErrors)
    if ($parseErrors.Count -gt 0) {
        throw "PowerShell syntax failure near document line $($block.Start): $($parseErrors.Message -join '; ')"
    }
}

# Exercise only the document's preflight guard, with a mocked Docker command.
# No document deployment, container mutation, or real environment-file operation runs here.
$guard = @($blocks | Where-Object { $_.Body.Contains('$existingVolumes = @(docker volume ls') })
if ($guard.Count -ne 1) { throw 'Expected exactly one target-machine preflight guard' }
$fixtureDir = Join-Path $PSScriptRoot 'fixtures'
New-Item -ItemType Directory -Path $fixtureDir -Force | Out-Null
$presentEnv = Join-Path $fixtureDir 'present.env'
[IO.File]::WriteAllText($presentEnv, "DB_PASSWORD=document-fixture-only`n")
$absentEnv = Join-Path $fixtureDir ('absent-' + [guid]::NewGuid().ToString('N') + '.env')
$guardCases = @(
    @{Name='fresh';Volumes=@();EnvPath=$absentEnv;Exit=0;Blocked=$false},
    @{Name='existing-volume-missing-config';Volumes=@('fixture-volume');EnvPath=$absentEnv;Exit=0;Blocked=$true},
    @{Name='existing-volume-existing-config';Volumes=@('fixture-volume');EnvPath=$presentEnv;Exit=0;Blocked=$false},
    @{Name='prepared-config-new-volume';Volumes=@();EnvPath=$presentEnv;Exit=0;Blocked=$false},
    @{Name='docker-inspection-failed';Volumes=@();EnvPath=$absentEnv;Exit=1;Blocked=$true}
)
$guardResults = foreach ($case in $guardCases) {
    $caught = $false
    try {
        & {
            param($Case, $Body)
            $EnvFile = $Case.EnvPath
            $FixtureVolumes = $Case.Volumes
            $LASTEXITCODE = $Case.Exit
            function docker { $FixtureVolumes }
            & ([scriptblock]::Create($Body)) | Out-Null
        } $case $guard[0].Body
    } catch {
        $caught = $true
        if ($_.Exception.Message -notmatch '发现已有|无法检查 Docker') { throw }
    }
    if ($caught -ne $case.Blocked) { throw "Preflight behavior mismatch: $($case.Name)" }
    [pscustomobject]@{Name=$case.Name;Passed=$true}
}

# A deliberately fake environment is used for local Compose parsing, never the private runtime file.
$fixtureEnv = Join-Path $fixtureDir 'compose-fixture.env'
$templateLines = [IO.File]::ReadAllLines((Join-Path $root 'deploy/docker/.env.example'))
[IO.File]::WriteAllLines($fixtureEnv, $templateLines, [Text.UTF8Encoding]::new($false))
$startInfo = [Diagnostics.ProcessStartInfo]::new()
$startInfo.FileName = (Get-Command docker.exe).Source
$startInfo.UseShellExecute = $false
$startInfo.CreateNoWindow = $true
$startInfo.RedirectStandardOutput = $true
$startInfo.RedirectStandardError = $true
foreach ($line in $templateLines) {
    if ($line -match '^([A-Z][A-Z0-9_]*)=(.*)$') {
        $startInfo.Environment[$Matches[1]] = $Matches[2]
    }
}
foreach ($arg in @('compose','--project-name','semi-overt-doc-validation','--profile','tools','--env-file',$fixtureEnv,
    '--file',(Join-Path $root 'deploy/docker/compose.yml'),'config','--format','json')) {
    $startInfo.ArgumentList.Add($arg)
}
$process = [Diagnostics.Process]::Start($startInfo)
$outputTask = $process.StandardOutput.ReadToEndAsync()
$errorTask = $process.StandardError.ReadToEndAsync()
$process.WaitForExit()
$json = $outputTask.GetAwaiter().GetResult()
$errorText = $errorTask.GetAwaiter().GetResult()
if ($process.ExitCode -ne 0) { throw "Fixture Compose validation failed: $errorText" }
$process.Dispose()
$compose = $json | ConvertFrom-Json -AsHashtable
$expectedServices = @('auth-service','content-service','review-service','search-service','file-service','notification-service','gateway')
foreach ($service in $expectedServices) {
    if (-not $compose.services.ContainsKey($service)) { throw "Missing documented service: $service" }
}
if ($compose.services.adminer.profiles -notcontains 'tools') { throw 'Adminer profile contract changed' }
$coreImages = @($compose.services.Values | Where-Object { -not $_.ContainsKey('profiles') } | ForEach-Object image | Sort-Object -Unique)
foreach ($image in $coreImages) {
    if (-not $text.Contains($image)) { throw "Core image missing from documentation: $image" }
}
foreach ($key in @('DEEPSEEK_API_KEY','DEEPSEEK_BASE_URL','DEEPSEEK_MODEL','DEEPSEEK_PROXY_URL','AI_POLISH_ENDPOINT','AI_POLISH_TIMEOUT_SECONDS','AI_POLISH_MAX_TOKENS')) {
    if (-not $compose.services.'content-service'.environment.ContainsKey($key)) { throw "AI variable contract missing: $key" }
}
for ($number = 1; $number -le 15; $number++) {
    if ($text -notmatch "(?m)^## $number\.") { throw "Missing section $number" }
}
$result = [ordered]@{
    Document=$docPath
    Lines=$lines.Count
    PowerShellBlocks=@($blocks | Where-Object Language -eq 'powershell').Count
    CodeFencesBalanced=$true
    MarkdownTablesAligned=$true
    PreflightGuardCases=@($guardResults)
    ComposeValidation='passed-with-fake-config-no-containers-started'
    JavaServiceContracts=$expectedServices.Count
    CoreImages=$coreImages.Count
    SourceSha256=(Get-FileHash -LiteralPath $docPath).Hash
    VerifiedAt=(Get-Date -Format o)
}
$result | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $PSScriptRoot 'validation.json') -Encoding utf8NoBOM
$result | ConvertTo-Json -Depth 5
