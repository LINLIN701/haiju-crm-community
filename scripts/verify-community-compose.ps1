[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"

$appRoot = [System.IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
$expectedRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot ".."))
if (-not $appRoot.Equals($expectedRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "社区版目录解析异常，已停止 Compose 验证：$appRoot"
}

$composeFile = Join-Path $appRoot "docker-compose.yml"
if (-not (Test-Path -LiteralPath $composeFile)) {
    throw "未找到社区版 docker-compose.yml：$composeFile"
}

$dockerStatus = docker info --format "{{.ServerVersion}}" 2>$null
if ($LASTEXITCODE -ne 0 -or -not $dockerStatus) {
    throw "Docker 引擎未运行，无法执行社区版 Compose 验证。"
}

$projectName = "haiju-community-release-check-$(Get-Date -Format 'yyyyMMddHHmmss')"
$env:MYSQL_DATABASE = "haiju_community_release_check"
$env:MYSQL_USER = "haiju_release"
$env:MYSQL_PASSWORD = "$(New-Guid)Aa1!"
$env:MYSQL_ROOT_PASSWORD = "$(New-Guid)Bb2!"
$env:APP_ADMIN_USERNAME = "releasecheck"
$env:APP_ADMIN_PASSWORD = "$(New-Guid)Cc3!"
$env:APP_CORS_ALLOWED_ORIGIN = "http://localhost:8088"

function Invoke-CommunityRequest {
    param(
        [Parameter(Mandatory)]
        [string]$Uri,
        [string]$Method = "GET",
        [hashtable]$Headers = @{},
        [string]$Body
    )

    $params = @{
        Uri = $Uri
        Method = $Method
        Headers = $Headers
        SkipHttpErrorCheck = $true
        UseBasicParsing = $true
    }
    if ($PSBoundParameters.ContainsKey("Body")) {
        $params.ContentType = "application/json;charset=UTF-8"
        $params.Body = $Body
    }
    Invoke-WebRequest @params
}

Push-Location $appRoot
try {
    docker compose -p $projectName up -d --build
    if ($LASTEXITCODE -ne 0) {
        throw "社区版 docker compose up 失败。"
    }

    $ready = $false
    for ($attempt = 1; $attempt -le 48; $attempt++) {
        try {
            $health = Invoke-CommunityRequest -Uri "http://localhost:8088/api/v1/health"
            if ($health.StatusCode -eq 200) {
                $ready = $true
                break
            }
        }
        catch {
            # 容器启动过程中允许短暂连接失败，最终仍按超时失败关闭。
        }
        Start-Sleep -Seconds 5
    }
    if (-not $ready) {
        docker compose -p $projectName ps
        docker compose -p $projectName logs --tail 120 backend
        throw "社区版 Compose 未在 240 秒内通过健康检查。"
    }

    $credentialBytes = [System.Text.Encoding]::UTF8.GetBytes("$($env:APP_ADMIN_USERNAME):$($env:APP_ADMIN_PASSWORD)")
    $authorization = "Basic $([Convert]::ToBase64String($credentialBytes))"
    $authHeaders = @{ Authorization = $authorization }

    $frontend = Invoke-CommunityRequest -Uri "http://localhost:8088/"
    $unauthenticated = Invoke-CommunityRequest -Uri "http://localhost:8088/api/v1/customers"
    $dashboard = Invoke-CommunityRequest -Uri "http://localhost:8088/api/v1/dashboard/overview" -Headers $authHeaders
    $ai = Invoke-CommunityRequest `
        -Uri "http://localhost:8088/api/v1/ai/parse-customer" `
        -Method "POST" `
        -Headers $authHeaders `
        -Body '{"text":"虚构客户王女士，手机号13800009999"}'

    if ($frontend.StatusCode -ne 200) {
        throw "社区前端验收失败：HTTP $($frontend.StatusCode)"
    }
    if ($unauthenticated.StatusCode -ne 401) {
        throw "未登录鉴权验收失败：期望 401，实际 $($unauthenticated.StatusCode)"
    }
    if ($dashboard.StatusCode -ne 200) {
        throw "管理员看板验收失败：HTTP $($dashboard.StatusCode)"
    }
    if ($ai.StatusCode -ne 503 -or $ai.Content -notmatch '外部模型未配置') {
        throw "AI 未配置失败语义验收失败：HTTP $($ai.StatusCode)"
    }

    $migrationCount = docker compose -p $projectName exec -T `
        -e "MYSQL_PWD=$($env:MYSQL_ROOT_PASSWORD)" `
        mysql mysql -uroot -Nse "SELECT COUNT(*) FROM haiju_community_release_check.flyway_schema_history WHERE success = 1;"
    if ($LASTEXITCODE -ne 0 -or [int]$migrationCount -lt 1) {
        throw "MySQL 8.4 Flyway 空库迁移验收失败。"
    }

    Write-Host "社区版 Compose 验证通过："
    Write-Host "- Docker Server：$dockerStatus"
    Write-Host "- 前端首页：HTTP $($frontend.StatusCode)"
    Write-Host "- 健康检查：HTTP $($health.StatusCode)"
    Write-Host "- 未登录客户接口：HTTP $($unauthenticated.StatusCode)"
    Write-Host "- 管理员看板：HTTP $($dashboard.StatusCode)"
    Write-Host "- AI 未配置：HTTP $($ai.StatusCode)，明确失败"
    Write-Host "- MySQL 8.4 Flyway 成功迁移：$migrationCount"
}
finally {
    docker compose -p $projectName down -v --remove-orphans
    Pop-Location
}
