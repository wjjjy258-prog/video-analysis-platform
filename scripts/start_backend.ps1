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
    Write-Warning "MYSQL_PASSWORD is empty."
    $secure = Read-Host "Please enter MySQL password for user '$MysqlUser' (leave blank if no password)" -AsSecureString
    $bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try {
        $MysqlPassword = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr)
    } finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)
    }
}

$env:MYSQL_USER = $MysqlUser
$env:MYSQL_PASSWORD = $MysqlPassword

if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
    Write-Error "Maven (mvn) not found. Install JDK 17+ and Maven 3.9+ first."
    exit 1
}

Set-Location $backendPath
mvn spring-boot:run
