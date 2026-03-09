param(
    [string]$MysqlHost = "localhost",
    [int]$MysqlPort = 3306,
    [string]$MysqlUser = "root",
    [string]$MysqlPassword = $env:MYSQL_PASSWORD,
    [string]$MysqlDb = "video_analysis",
    [string]$AuthUser = "demo",
    [string]$AuthPassword = "123456",
    [bool]$RestartBackend = $true,
    [int]$ApiWaitSeconds = 180,
    [int]$FrontWaitSeconds = 120,
    [int]$SmokeApiTimeoutSeconds = 45,
    [int]$SmokeApiRetries = 2,
    [switch]$StrictSmokeTest
)

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
$initScript = Join-Path $PSScriptRoot "init_db_python.py"
$smokeScript = Join-Path $PSScriptRoot "smoke_test.py"
$exportScript = Join-Path $PSScriptRoot "export_analysis_report.py"
$backendScript = Join-Path $PSScriptRoot "start_backend.ps1"
$frontendScript = Join-Path $PSScriptRoot "start_frontend.ps1"
$backendHealthUrl = "http://localhost:8080/health"
$frontendUrl = "http://localhost:5173"
$reportDir = Join-Path $projectRoot "analysis\\reports"

function Write-Step([string]$Message) {
    Write-Host "==> $Message" -ForegroundColor Cyan
}

function Test-Url([string]$Url, [int]$TimeoutSec = 3) {
    try {
        $resp = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec $TimeoutSec
        return $resp.StatusCode -ge 200 -and $resp.StatusCode -lt 500
    } catch {
        return $false
    }
}

function Wait-Url([string]$Url, [int]$MaxSeconds) {
    $elapsed = 0
    while ($elapsed -lt $MaxSeconds) {
        if (Test-Url -Url $Url -TimeoutSec 3) {
            return $true
        }
        Start-Sleep -Seconds 3
        $elapsed += 3
    }
    return (Test-Url -Url $Url -TimeoutSec 3)
}

function Get-ListeningPidsByPort([int]$Port) {
    $pids = @()
    try {
        $rows = Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue
        if ($rows) {
            $pids += $rows | Select-Object -ExpandProperty OwningProcess
        }
    } catch {
        # Ignore and fallback to netstat
    }

    if (-not $pids -or $pids.Count -eq 0) {
        try {
            $lines = netstat -ano | Select-String -Pattern "LISTENING\\s+([0-9]+)$"
            foreach ($line in $lines) {
                $text = $line.ToString()
                if ($text -match "[:\.]$Port\\s+.*LISTENING\\s+([0-9]+)$") {
                    $pids += [int]$Matches[1]
                }
            }
        } catch {
            # Ignore
        }
    }

    return @($pids | Where-Object { $_ -and $_ -gt 0 } | Select-Object -Unique)
}

function Stop-ListeningProcessOnPort([int]$Port, [string]$ServiceName) {
    $pids = Get-ListeningPidsByPort -Port $Port
    if (-not $pids -or $pids.Count -eq 0) {
        return
    }
    foreach ($procId in $pids) {
        try {
            Write-Step "Stopping $ServiceName process on port $Port (PID=$procId)"
            Stop-Process -Id $procId -Force -ErrorAction SilentlyContinue
        } catch {
            Write-Warning "Failed to stop PID=$procId on port $Port."
        }
    }
    Start-Sleep -Seconds 1
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

Write-Step "Initializing database (without mysql CLI)"
python $initScript --host $MysqlHost --port $MysqlPort --user $MysqlUser --password $MysqlPassword
if ($LASTEXITCODE -ne 0) {
    throw "Database initialization failed."
}

if ($RestartBackend) {
    Stop-ListeningProcessOnPort -Port 8080 -ServiceName "backend"
}

$backendReady = Test-Url -Url $backendHealthUrl -TimeoutSec 3
if (-not $backendReady) {
    Write-Step "Starting backend"
    if (Get-Command mvn -ErrorAction SilentlyContinue) {
        Start-Process powershell -ArgumentList "-NoExit", "-ExecutionPolicy", "Bypass", "-File", "`"$backendScript`""
        $backendReady = Wait-Url -Url $backendHealthUrl -MaxSeconds $ApiWaitSeconds
        if ($backendReady) {
            Write-Step "Backend is ready"
        } else {
            Write-Warning "Backend start timeout. API checks will be skipped."
        }
    } else {
        Write-Warning "mvn not found. Backend was not started."
    }
} else {
    Write-Step "Backend is already running"
}

$frontendReady = Test-Url -Url $frontendUrl -TimeoutSec 3
if ($frontendReady) {
    Write-Step "Frontend is already running"
} else {
    Write-Step "Starting frontend"
    if (Get-Command npm.cmd -ErrorAction SilentlyContinue) {
        Start-Process powershell -ArgumentList "-NoExit", "-ExecutionPolicy", "Bypass", "-File", "`"$frontendScript`""
        $frontendReady = Wait-Url -Url $frontendUrl -MaxSeconds $FrontWaitSeconds
        if ($frontendReady) {
            Write-Step "Frontend is ready"
        } else {
            Write-Warning "Frontend start timeout. Check the frontend console window."
        }
    } else {
        Write-Warning "npm.cmd not found. Frontend was not started."
    }
}

Write-Step "Running smoke test"
$smokeArgs = @(
    "--mysql-host", $MysqlHost,
    "--mysql-port", $MysqlPort,
    "--mysql-user", $MysqlUser,
    "--mysql-password", $MysqlPassword,
    "--mysql-db", $MysqlDb,
    "--api-base", "http://localhost:8080",
    "--api-timeout", $SmokeApiTimeoutSeconds,
    "--api-retries", $SmokeApiRetries,
    "--auth-user", $AuthUser,
    "--auth-password", $AuthPassword,
    "--report-dir", $reportDir
)
if (-not $backendReady) {
    $smokeArgs += "--skip-api"
}
python $smokeScript @smokeArgs
if ($LASTEXITCODE -ne 0) {
    if ($StrictSmokeTest) {
        throw "Smoke test failed."
    } else {
        Write-Warning "Smoke test failed. Services may still be running; check backend logs."
    }
}

Write-Step "Exporting analysis report (CSV + MD)"
python $exportScript --mysql-host $MysqlHost --mysql-port $MysqlPort --mysql-user $MysqlUser --mysql-password $MysqlPassword --mysql-db $MysqlDb --output-dir $reportDir
if ($LASTEXITCODE -ne 0) {
    Write-Warning "Report export failed. You can run scripts\\export_analysis_report.py manually."
}

Write-Step "Opening frontend page"
Start-Process $frontendUrl

Write-Host ""
Write-Host "Full auto launch completed." -ForegroundColor Green

