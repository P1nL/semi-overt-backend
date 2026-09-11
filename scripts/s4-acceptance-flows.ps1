# Dot-sourced only by the isolated S4 acceptance harness.
function Login-Fixtures {
    $tokens=@{}
    foreach($name in @('s4writer','s4visitor','s4admin','s4otheradmin')){
        $r=Api POST '/api/v1/auth/login' @{account=$name;password=$env:S4_LOGIN_PASSWORD;rememberMe=$false}
        Check "durable login $name" ($r.status -eq 200)
        $tokens[$name]=$r.json.data.token
    }
    return $tokens
}
function Run-PackageOne($Tokens) {
    foreach($prefix in @('/api','/api/v1')) {
        $public=Api GET "$prefix/users/s4writer/profile?pageSize=100&tab=draft"
        Check "$prefix public profile resolves username and omits email" ($public.status -eq 200 -and $public.json.data.profile.username -eq 's4writer' -and -not ($public.json.data.profile.PSObject.Properties.Name -contains 'email'))
        Check "$prefix public profile cannot enumerate drafts" (@($public.json.data.list | Where-Object {$_.status -ne 'APPROVED'}).Count -eq 0 -and $public.json.data.stats.draft -eq 0 -and $public.json.data.stats.pending -eq 0)
        Check "$prefix calendar only public updated_at days" ($public.json.data.writingCalendar.Count -eq 1 -and $public.json.data.writingCalendar[0].date -eq (Get-Date -Format 'yyyy-MM-dd'))
        Check "$prefix public statistics and calendar exclude deleted/private/old days" ($public.json.data.stats.approved -eq 22 -and $public.json.data.stats.totalWordCount -eq 2431 -and $public.json.data.writingCalendar[0].wordCount -eq 2310)
        $numeric=Api GET "$prefix/users/101/profile"
        Check "$prefix numeric profile uses same authority" ($numeric.status -eq 200 -and $numeric.json.data.profile.id -eq 101)
        $self=Api GET "$prefix/users/s4writer/profile?tab=all&pageSize=100" $null $Tokens.s4writer
        Check "$prefix owner sees private counts and calendar" ($self.status -eq 200 -and $self.json.data.stats.draft -eq 1 -and $self.json.data.stats.pending -eq 1 -and $self.json.data.writingCalendar.Count -eq 2)
        $adminProfile=Api GET "$prefix/users/s4writer/profile?tab=all&pageSize=100" $null $Tokens.s4admin
        Check "$prefix administrator may inspect profile aggregates" ($adminProfile.status -eq 200 -and $adminProfile.json.data.stats.draft -eq 1)
        Check "$prefix profile aggregate permission never opens private draft body" ((Api GET "$prefix/articles/1024" $null $Tokens.s4admin).status -in @(403,404))
        $visitor=Api GET "$prefix/users/s4writer/profile?tab=all" $null $Tokens.s4visitor
        Check "$prefix other member has public scope" ($visitor.status -eq 200 -and $visitor.json.data.stats.draft -eq 0)
        $invalid=Api GET "$prefix/users/999999999999999999999999999999/profile"
        Check "$prefix numeric overflow returns not-found not 500" ($invalid.status -eq 404)
        $limit=Api GET "$prefix/users/101/profile?page=0&pageSize=0&limit=2"
        Check "$prefix source-compatible limit fallback and page normalization" ($limit.status -eq 200 -and $limit.json.data.page -eq 1 -and $limit.json.data.pageSize -eq 2 -and $limit.json.data.list.Count -eq 2)
    }
    $updated=Api PUT '/api/users/me' @{nickname='星云作者已更新';signature='S4 owner update';coverUrl='https://example.invalid/fixture-cover.png'} $Tokens.s4writer
    Check 'profile update aliases and returned fields' ($updated.status -eq 200 -and $updated.json.data.nickname -eq '星云作者已更新' -and $updated.json.data.signature -eq 'S4 owner update')
    $blank=Api PUT '/api/v1/users/me' @{nickname=' ';signature=' ';coverUrl=' '} $Tokens.s4writer
    Check 'blank profile patch preserves current fields' ($blank.status -eq 200 -and $blank.json.data.nickname -eq '星云作者已更新' -and $blank.json.data.signature -eq 'S4 owner update')
    Check 'unauthorized profile update denied' ((Api PUT '/api/v1/users/me' @{nickname='intruder'}).status -eq 401)
    $before=[int](DbQuery 'SELECT COUNT(*) FROM home_article_exposures')[0]
    $anon=Api GET '/api/home'
    Check 'anonymous home renders 11 per category at most' ($anon.status -eq 200 -and @($anon.json.data.sections | Where-Object {$_.category -eq 'QUICK'})[0].list.Count -eq 11)
    Check 'anonymous home does not record user exposures' ([int](DbQuery 'SELECT COUNT(*) FROM home_article_exposures')[0] -eq $before)
    $one=Api GET '/api/v1/home' $null $Tokens.s4writer
    $first=@($one.json.data.hero.primary.id)+@($one.json.data.hero.secondary | ForEach-Object {$_.id})
    Check 'authenticated home records exactly hero exposures' ([int](DbQuery 'SELECT COUNT(*) FROM home_article_exposures WHERE user_id=101')[0] -eq 11)
    $two=Api GET '/api/v1/home' $null $Tokens.s4writer
    $second=@($two.json.data.hero.primary.id)+@($two.json.data.hero.secondary | ForEach-Object {$_.id})
    Check 'repeat home prefers unseen user articles' (@($second | Where-Object {$_ -notin $first}).Count -gt 0)
    $other=Api GET '/api/v1/home' $null $Tokens.s4visitor
    Check 'other user exposure state is independent' ([int](DbQuery 'SELECT COUNT(*) FROM home_article_exposures WHERE user_id=102')[0] -eq 11)
    Check 'home never mutates article global featured/version state' ([int](DbQuery 'SELECT COUNT(*) FROM articles WHERE last_featured_at IS NOT NULL OR version<>0')[0] -eq 0)
    @{public=$public.json.data;owner=$self.json.data;home=$one.json.data}|ConvertTo-Json -Depth 15|Set-Content -LiteralPath (Join-Path $run 'package1-wire.json') -Encoding utf8
}
function Run-PackageTwo($Tokens) {
    $untrusted=Invoke-WebRequest 'http://127.0.0.1:20084/internal/search/capability' -SkipHttpErrorCheck
    Check 'search capability cannot be read without internal token' ([int]$untrusted.StatusCode -eq 403)
    $before=Invoke-RestMethod 'http://127.0.0.1:20084/internal/search/capability' -Headers @{'X-Internal-Token'=$env:INTERNAL_TOKEN}
    Check 'optional fulltext is requested but absent before fixture DDL' ($before.data.requested -and -not $before.data.indexPresent -and -not $before.data.usable)
    foreach($prefix in @('/api','/api/v1')) {
        $r=Api GET "$prefix/search?keyword=nebula&pageSize=50"
        Check "$prefix body search returns all semantic hits" ($r.status -eq 200 -and $r.json.data.total -eq 3 -and $r.json.data.list.Count -eq 3)
        Check "$prefix exact title outranks prefix and body" ($r.json.data.list[0].id -eq 1001 -and $r.json.data.list[1].id -eq 1002 -and $r.json.data.list[2].id -eq 1003)
        foreach($keyword in @('hiddenphantom','hiddenurlphantom','privatephantom')) {
            $hidden=Api GET "$prefix/search?keyword=$keyword"
            Check "$prefix excludes $keyword from search" ($hidden.status -eq 200 -and $hidden.json.data.total -eq 0)
        }
        $page=Api GET "$prefix/search?keyword=nebula&page=2&pageSize=1"
        Check "$prefix pagination retains total/rank" ($page.status -eq 200 -and $page.json.data.total -eq 3 -and $page.json.data.list.Count -eq 1 -and $page.json.data.list[0].id -eq 1002)
        $users=Api GET "$prefix/search/users?keyword=s4writer"
        Check "$prefix user search preserves public fields only" ($users.status -eq 200 -and $users.json.data.total -eq 1 -and -not ($users.json.data.list[0].PSObject.Properties.Name -contains 'email'))
    }
    & java -Xmx128m -cp $script:fixtureClasspath S4DatabaseFixture enable-fulltext
    if($LASTEXITCODE -ne 0){throw 'Optional fulltext fixture creation failed'}
    $capability=Invoke-RestMethod 'http://127.0.0.1:20084/internal/search/capability' -Headers @{'X-Internal-Token'=$env:INTERNAL_TOKEN}
    Check 'real fulltext capability switches to usable' ($capability.data.requested -and $capability.data.indexPresent -and $capability.data.usable)
    $indexed=Api GET '/api/v1/search?keyword=nebula&pageSize=50'
    Check 'real ngram FULLTEXT index keeps semantic ranking' ($indexed.status -eq 200 -and $indexed.json.data.total -eq 3 -and $indexed.json.data.list[0].id -eq 1001)
    & java -Xmx128m -cp $script:fixtureClasspath S4DatabaseFixture disable-fulltext
    if($LASTEXITCODE -ne 0){throw 'Optional fulltext fixture removal failed'}
    $after=Invoke-RestMethod 'http://127.0.0.1:20084/internal/search/capability' -Headers @{'X-Internal-Token'=$env:INTERNAL_TOKEN}
    Check 'dropping optional index switches back to observed fallback' (-not $after.data.indexPresent -and -not $after.data.usable)
    $fallback=Api GET '/api/v1/search?keyword=nebula&pageSize=50'
    Check 'removed optional index falls back without losing results' ($fallback.status -eq 200 -and $fallback.json.data.total -eq 3)
    @{indexed=$indexed.json;fallback=$fallback.json;capability=$capability.data}|ConvertTo-Json -Depth 15|Set-Content -LiteralPath (Join-Path $run 'package2-wire.json') -Encoding utf8
}
