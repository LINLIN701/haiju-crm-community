[CmdletBinding()]
param(
    [string]$RepositoryRoot
)

$ErrorActionPreference = "Stop"

if (-not $RepositoryRoot) {
    $candidate = Split-Path -Parent $PSScriptRoot
    if (Test-Path -LiteralPath (Join-Path $candidate "LICENSE")) {
        $RepositoryRoot = $candidate
    }
    else {
        $RepositoryRoot = Split-Path -Parent $candidate
    }
}

$RepositoryRoot = [System.IO.Path]::GetFullPath($RepositoryRoot)
if (-not (Test-Path -LiteralPath (Join-Path $RepositoryRoot "LICENSE"))) {
    throw "公开仓库审计未找到 LICENSE：$RepositoryRoot"
}

$excludedGlobs = @(
    "!**/.git/**",
    "!**/node_modules/**",
    "!**/dist/**",
    "!**/target/**",
    "!**/.runtime/**",
    "!**/audit-public-repository.ps1",
    "!**/verify-community-source.ps1"
)

function Find-TextFiles {
    param([string]$Pattern, [switch]$Pcre2, [switch]$IgnoreCase)

    $arguments = @("-l", "--hidden")
    if ($Pcre2) { $arguments += "-P" }
    if ($IgnoreCase) { $arguments += "-i" }
    foreach ($glob in $excludedGlobs) {
        $arguments += @("-g", $glob)
    }
    $arguments += @($Pattern, "--", $RepositoryRoot)
    return @(rg @arguments 2>$null)
}

$proprietaryTerms = "货宝宝|淘淘乐园|乐淘|淘淘沙龙|沙龙主理人|城市主理人|社区主理人"
$proprietaryHits = Find-TextFiles -Pattern $proprietaryTerms
if ($proprietaryHits.Count -gt 0) {
    throw "公开候选仍包含原组织专属称谓：$($proprietaryHits -join ', ')"
}

$highConfidenceSecretPatterns = @(
    "AKIA[0-9A-Z]{16}",
    "ASIA[0-9A-Z]{16}",
    "gh[pousr]_[A-Za-z0-9_]{30,}",
    "github_pat_[A-Za-z0-9_]{40,}",
    "sk-(?:proj-)?[A-Za-z0-9_-]{20,}",
    "AIza[0-9A-Za-z_-]{35}",
    "xox[baprs]-[A-Za-z0-9-]{20,}",
    "-----BEGIN (?:RSA |EC |OPENSSH |DSA )?PRIVATE KEY-----"
)
$secretHits = @()
foreach ($pattern in $highConfidenceSecretPatterns) {
    $secretHits += Find-TextFiles -Pattern $pattern -Pcre2
}
$secretHits = @($secretHits | Sort-Object -Unique)
if ($secretHits.Count -gt 0) {
    throw "公开候选疑似包含高置信度凭据：$($secretHits -join ', ')"
}

$forbiddenFilePatterns = @(".env", "*.pem", "*.pfx", "*.p12", "*.jks", "*.keystore", "*.sqlite", "*.sqlite3", "*.db", "*.bak", "*.dump")
$forbiddenFiles = foreach ($pattern in $forbiddenFilePatterns) {
    Get-ChildItem -LiteralPath $RepositoryRoot -Recurse -Force -File -Filter $pattern -ErrorAction SilentlyContinue | Where-Object {
        $_.FullName -notmatch "[\\/](node_modules|dist|target|\.git)[\\/]"
    }
}
if ($forbiddenFiles) {
    throw "公开候选包含环境、证书或数据文件：$($forbiddenFiles.FullName -join ', ')"
}

$unexpectedSql = Get-ChildItem -LiteralPath $RepositoryRoot -Recurse -Force -File -Filter "*.sql" | Where-Object {
    $_.FullName -notmatch "[\\/](node_modules|dist|target|\.git)[\\/]" -and
    $_.FullName -notmatch "[\\/]backend[\\/]src[\\/]main[\\/]resources[\\/]db[\\/]migration[\\/]"
}
if ($unexpectedSql) {
    throw "公开候选包含非 Flyway 迁移 SQL 文件：$($unexpectedSql.FullName -join ', ')"
}

$largeFiles = Get-ChildItem -LiteralPath $RepositoryRoot -Recurse -Force -File | Where-Object {
    $_.FullName -notmatch "[\\/](node_modules|dist|target|\.git)[\\/]" -and $_.Length -gt 10MB
}
if ($largeFiles) {
    throw "公开候选包含超过 10 MiB 的文件：$($largeFiles.FullName -join ', ')"
}

$phoneHits = Find-TextFiles -Pattern "(?<![0-9])1[3-9][0-9]{9}(?![0-9])" -Pcre2
$unexpectedPhones = @($phoneHits | Where-Object {
    $_ -notmatch "[\\/]src[\\/]test[\\/]" -and $_ -notmatch "verify-community-compose\.ps1$"
})
if ($unexpectedPhones.Count -gt 0) {
    throw "公开候选的非测试文件疑似包含中国大陆手机号：$($unexpectedPhones -join ', ')"
}

Write-Host "公开仓库审计通过：专属称谓、高置信度凭据、环境/证书/数据文件、异常 SQL、大文件和非测试手机号门禁均通过。"
