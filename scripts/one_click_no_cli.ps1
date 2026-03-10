param(
    [string]$MysqlHost = "localhost",
    [int]$MysqlPort = 3306,
    [string]$MysqlUser = "root",
    [string]$MysqlPassword = $env:MYSQL_PASSWORD,
    [string]$MysqlDb = "video_analysis",
    [string]$AuthUser = "demo",
    [string]$AuthPassword = "123456",
    [int]$ApiWaitSeconds = 180
)

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
$initScript = Join-Path $PSScriptRoot "init_db_python.py"
$smokeScript = Join-Path $PSScriptRoot "smoke_test.py"
$exportScript = Join-Path $PSScriptRoot "export_analysis_report.py"
$backendScript = Join-Path $PSScriptRoot "start_backend.ps1"
$healthUrl = "http://localhost:8080/health"
$reportDir = Join-Path $projectRoot "analysis\\reports"

function Write-Step([string]$Message) {
    Write-Host "==> $Message" -ForegroundColor Cyan
}

function Test-Health {
    try {
        $resp = Invoke-WebRequest -Uri $healthUrl -UseBasicParsing -TimeoutSec 3
        return $resp.StatusCode -eq 200
    } catch {
        return $false
    }
}

function Ensure-MySqlPassword([string]$CurrentPassword, [string]$UserName) {
    if (-not [string]::IsNullOrWhiteSpace($CurrentPassword)) {
        return $CurrentPassword
    }
    Write-Warning "MYSQL_PASSWORD is empty."
    $secure = Read-Host "Please enter MySQL password for user '$UserName' (leave blank if no password)" -AsSecureString
    $bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try {
        return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr)
    } finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)
    }
}
Write-Step "Checking Python"
if (-not (Get-Command python -ErrorAction SilentlyContinue)) {
    throw "Python not found. Please install Python 3.10+."
}

Write-Step "Installing required Python packages"
python -m pip install --disable-pip-version-check mysql-connector-python requests
if ($LASTEXITCODE -ne 0) {
    throw "Package install failed."
}

$MysqlPassword = Ensure-MySqlPassword -CurrentPassword $MysqlPassword -UserName $MysqlUser
$env:MYSQL_USER = $MysqlUser
$env:MYSQL_PASSWORD = $MysqlPassword

Write-Step "Initializing database (without mysql CLI)"
$initArgs = @(
    "--host", $MysqlHost,
    "--port", $MysqlPort,
    "--user", $MysqlUser
)
if (-not [string]::IsNullOrWhiteSpace($MysqlPassword)) {
    $initArgs += @("--password", $MysqlPassword)
}
python $initScript @initArgs
if ($LASTEXITCODE -ne 0) {
    throw "Database initialization failed."
}

if (Test-Health) {
    Write-Step "Backend is already running"
} else {
    Write-Step "Backend is not running, trying to start it"
    $skipApi = $false
    if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
        Write-Warning "mvn not found. Will run DB-only self-check and skip API checks."
        $skipApi = $true
    } else {
        Start-Process powershell -ArgumentList "-NoExit", "-ExecutionPolicy", "Bypass", "-File", "`"$backendScript`""

        $elapsed = 0
        while ($elapsed -lt $ApiWaitSeconds) {
            Start-Sleep -Seconds 3
            $elapsed += 3
            if (Test-Health) {
                Write-Step "Backend started"
                break
            }
        }
        if (-not (Test-Health)) {
            Write-Warning "Backend start timeout. Will run DB-only self-check and skip API checks."
            $skipApi = $true
        }
    }
}

Write-Step "Running smoke test"
$smokeArgs = @(
    "--mysql-host", $MysqlHost,
    "--mysql-port", $MysqlPort,
    "--mysql-user", $MysqlUser,
    "--mysql-db", $MysqlDb,
    "--api-base", "http://localhost:8080",
    "--auth-user", $AuthUser,
    "--auth-password", $AuthPassword,
    "--report-dir", $reportDir
)
if (-not [string]::IsNullOrWhiteSpace($MysqlPassword)) {
    $smokeArgs += @("--mysql-password", $MysqlPassword)
}

if ($skipApi -or -not (Test-Health)) {
    $smokeArgs += "--skip-api"
}

python $smokeScript @smokeArgs
if ($LASTEXITCODE -ne 0) {
    throw "Smoke test failed."
}

Write-Step "Exporting analysis report (CSV + MD)"
$exportArgs = @(
    "--mysql-host", $MysqlHost,
    "--mysql-port", $MysqlPort,
    "--mysql-user", $MysqlUser,
    "--mysql-db", $MysqlDb,
    "--output-dir", $reportDir
)
if (-not [string]::IsNullOrWhiteSpace($MysqlPassword)) {
    $exportArgs += @("--mysql-password", $MysqlPassword)
}
python $exportScript @exportArgs
if ($LASTEXITCODE -ne 0) {
    Write-Warning "Report export failed. You can run scripts\\export_analysis_report.py manually."
}

Write-Host ""
Write-Host "One-click init and smoke test completed." -ForegroundColor Green


