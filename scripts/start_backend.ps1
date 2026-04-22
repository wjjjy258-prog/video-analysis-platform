param(
    [string]$MysqlUser = $env:MYSQL_USER,
    [string]$MysqlPassword = $env:MYSQL_PASSWORD
)

$projectRoot = Split-Path -Parent $PSScriptRoot
$backendPath = Join-Path $projectRoot "backend"

if ([string]::IsNullOrWhiteSpace($MysqlUser)) {
    $MysqlUser = "root"
}

if ([string]::IsNullOrWhiteSpace($MysqlPassword)) {
    Write-Warning "MYSQL_PASSWORD is not set. If your MySQL account has a password, set MYSQL_PASSWORD before running."
    $MysqlPassword = ""
}

$env:MYSQL_USER = $MysqlUser
$env:MYSQL_PASSWORD = $MysqlPassword

if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
    Write-Error "Maven (mvn) not found. Install JDK 17+ and Maven 3.9+ first."
    exit 1
}

Set-Location $backendPath
mvn spring-boot:run
