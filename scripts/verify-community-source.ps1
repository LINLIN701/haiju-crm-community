[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
$appRoot = Split-Path -Parent $PSScriptRoot
$frontend = Join-Path $appRoot "frontend"
$backend = Join-Path $appRoot "backend"

$required = @(
    "README.md",
    "DEPENDENCY_LICENSES.md",
    ".env.example",
    "docker-compose.yml",
    "frontend\package.json",
    "frontend\package-lock.json",
    "backend\pom.xml",
    "backend\src\main\resources\db\migration\V1__community_core.sql",
    "scripts\audit-public-repository.ps1",
    "scripts\verify-community-compose.ps1"
)
foreach ($item in $required) {
    if (-not (Test-Path -LiteralPath (Join-Path $appRoot $item))) {
        throw "缺少社区版必要文件：$item"
    }
}

$forbiddenTerms = "货宝宝|淘淘乐园|乐淘|淘淘沙龙|沙龙主理人|城市主理人|社区主理人"
$sourceHits = @(rg -l --hidden -g '!node_modules/**' -g '!dist/**' -g '!target/**' -g '!verify-community-source.ps1' -g '!audit-public-repository.ps1' $forbiddenTerms -- $appRoot 2>$null)
if ($sourceHits.Count -gt 0) {
    throw "社区源码仍包含原组织专属称谓：$($sourceHits -join ', ')"
}

$commercialCodeTerms = "performancechain|membershipcard|automationjob|hbb|salonactivity|selfsummary|worktarget"
$commercialHits = @(rg -l -i --hidden -g '!node_modules/**' -g '!dist/**' -g '!target/**' $commercialCodeTerms -- $frontend\src $backend\src 2>$null)
if ($commercialHits.Count -gt 0) {
    throw "社区源码仍包含商业模块代码标识：$($commercialHits -join ', ')"
}

& (Join-Path $PSScriptRoot "audit-public-repository.ps1")

Push-Location $frontend
try {
    npm ci
    if ($LASTEXITCODE -ne 0) { throw "社区前端依赖安装失败" }
    npm run build
    if ($LASTEXITCODE -ne 0) { throw "社区前端构建失败" }
    npm run audit:prod
    if ($LASTEXITCODE -ne 0) { throw "社区前端生产依赖审计失败" }
}
finally {
    Pop-Location
}

Push-Location $backend
try {
    if ([System.Environment]::OSVersion.Platform -eq [System.PlatformID]::Win32NT) {
        .\mvnw.cmd test
    }
    else {
        sh ./mvnw test
    }
    if ($LASTEXITCODE -ne 0) { throw "社区后端测试失败" }
}
finally {
    Pop-Location
}

Write-Host "社区版独立源码验证通过：前端构建、生产依赖审计、后端测试、迁移、专属称谓、商业代码标识和高置信度凭据门禁均通过。"
