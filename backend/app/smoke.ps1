# Modular monolith smoke test - all modules through single port 8080
$ErrorActionPreference = 'Continue'
$base = 'http://127.0.0.1:8080/api/v1'
$pass = @{}

function Sha256Hex([string]$s) {
    $sha = [System.Security.Cryptography.SHA256]::Create()
    $bytes = $sha.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($s))
    ($bytes | ForEach-Object { $_.ToString('x2') }) -join ''
}

function Login([string]$phone, [string]$pwd, [string]$tag) {
    $body = @{ phone = $phone; password = (Sha256Hex $pwd) } | ConvertTo-Json
    try {
        $r = Invoke-RestMethod -Method Post -Uri "$base/auth/login" -ContentType 'application/json; charset=utf-8' -Body $body
        $script:pass[$tag] = $r.data.access_token
        Write-Host ("[OK] login {0}: code={1} role={2}" -f $tag, $r.code, $r.data.user.role)
    } catch {
        Write-Host ("[FAIL] login {0}: {1}" -f $tag, $_.Exception.Message)
    }
}

function Check([string]$name, [string]$method, [string]$url, $token, $expected = 200, $body = $null) {
    $headers = @{}
    if ($token) { $headers['Authorization'] = "Bearer $token" }
    try {
        if ($method -eq 'GET') {
            $r = Invoke-WebRequest -Method Get -Uri $url -Headers $headers -UseBasicParsing
        } elseif ($null -ne $body) {
            $r = Invoke-WebRequest -Method $method -Uri $url -Headers $headers -UseBasicParsing `
                -ContentType 'application/json; charset=utf-8' -Body $body
        } else {
            $r = Invoke-WebRequest -Method $method -Uri $url -Headers $headers -UseBasicParsing
        }
        $ok = if ($r.StatusCode -eq $expected) { 'OK' } else { 'UNEXPECTED' }
        if ($r.Content -is [byte[]]) {
            # 二进制响应（如 xlsx 模板）：只校验状态码与魔数
            $snippet = 'binary ' + $r.Content.Length + 'B magic=' + [Text.Encoding]::ASCII.GetString($r.Content[0..([Math]::Min(3, $r.Content.Length - 1))])
        } else {
            $snippet = ($r.Content -replace '\s+', ' ')
            if ($snippet.Length -gt 120) { $snippet = $snippet.Substring(0, 120) }
        }
        Write-Host ("[{0}] {1}: HTTP {2} (expect {3}) {4}" -f $ok, $name, $r.StatusCode, $expected, $snippet)
    } catch {
        $resp = $_.Exception.Response
        if ($resp) {
            $code = [int]$resp.StatusCode
            $ok = if ($code -eq $expected) { 'OK' } else { 'FAIL' }
            Write-Host ("[{0}] {1}: HTTP {2} (expect {3})" -f $ok, $name, $code, $expected)
        } else {
            Write-Host ("[FAIL] {0}: {1}" -f $name, $_.Exception.Message)
        }
    }
}

Login '13900010001' 'Alumni@123'   'alumni'
Login '13900010004' 'Admin@123'    'college'
Login '13900010005' 'School@123'   'school'

Write-Host '--- auth ---'
Check 'auth/me (alumni)'        'GET' "$base/auth/me" $pass['alumni']
Check 'auth/me (no token=401)'  'GET' "$base/auth/me" $null 401

Write-Host '--- dicts ---'
Check 'dicts/college'           'GET' "$base/dicts/college" $pass['alumni']
Check 'dicts/grad_year (virtual)' 'GET' "$base/dicts/grad_year" $pass['alumni']
Check 'dicts write denied (403)' 'POST' "$base/dicts/college" $pass['alumni'] 403
Check 'dicts/college no token (401)' 'GET' "$base/dicts/college" $null 401

Write-Host '--- archives ---'
Check 'archives/my (alumni)'    'GET' "$base/archives/my" $pass['alumni']
Check 'archives admin list (college)' 'GET' "$base/archives" $pass['college']

Write-Host '--- reviews ---'
Check 'reviews list (college)'   'GET' "$base/reviews" $pass['college']

Write-Host '--- stats ---'
Check 'stats/overview (college)' 'GET' "$base/stats/overview" $pass['college']
Check 'stats/colleges (college)' 'GET' "$base/stats/colleges" $pass['college']
Check 'stats denied (alumni=403)' 'GET' "$base/stats/overview" $pass['alumni'] 403

Write-Host '--- consents ---'
Check 'consents/my (alumni)'     'GET' "$base/consents/my" $pass['alumni']

Write-Host '--- honors ---'
Check 'honors list (alumni)'     'GET' "$base/honors" $pass['alumni']

Write-Host '--- showcase / screen ---'
Check 'screen/feed (public)'     'GET' "$base/screen/feed" $null

Write-Host '--- imports ---'
Check 'imports/template (college)' 'GET' "$base/imports/template" $pass['college']
Check 'imports denied (alumni=403)' 'GET' "$base/imports/template" $pass['alumni'] 403

Write-Host '--- drafts ---'
$alumniHeaders = @{ Authorization = "Bearer $($pass['alumni'])" }
# 清理 alumni 的 archive 草稿，保证冒烟可重放（顺带覆盖 DELETE /drafts/{id}）
try {
    $mine = Invoke-RestMethod -Method Get -Uri "$base/drafts/my?biz_type=archive" -Headers $alumniHeaders
    foreach ($d in @($mine.data.list)) {
        if ($d) { Invoke-WebRequest -Method Delete -Uri "$base/drafts/$($d.id)" -Headers $alumniHeaders -UseBasicParsing | Out-Null }
    }
} catch { }
$draftBody = @{ biz_type = 'archive'; version = 0; payload = @{ title = 'smoke-draft'; content = 'hello'; attachment_ids = @() } } | ConvertTo-Json -Depth 5
Check 'drafts/sync first (alumni)'  'POST' "$base/drafts/sync" $pass['alumni'] 200 $draftBody
Check 'drafts/sync conflict (409)'  'POST' "$base/drafts/sync" $pass['alumni'] 409 $draftBody
Check 'drafts/my (alumni)'          'GET'  "$base/drafts/my?biz_type=archive" $pass['alumni']
Check 'drafts/sync no token (401)'  'POST' "$base/drafts/sync" $null 401 $draftBody

Write-Host '--- showcase admin ---'
Check 'showcase publish denied (alumni=403)' 'POST' "$base/showcase/999/publish" $pass['alumni'] 403

Write-Host '--- done ---'
