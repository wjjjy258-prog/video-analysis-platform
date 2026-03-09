param(
    [string]$MysqlExe = "mysql",
    [string]$Host = "localhost",
    [int]$Port = 3306,
    [string]$User = "root",
    [string]$Password = $env:MYSQL_PASSWORD
)

$projectRoot = Split-Path -Parent $PSScriptRoot
$sqlPath = Join-Path $projectRoot "database\\init.sql"
$pythonInit = Join-Path $PSScriptRoot "init_db_python.py"

if (-not (Test-Path $sqlPath)) {
    Write-Error "SQL file not found: $sqlPath"
    exit 1
}

$mysqlCmd = Get-Command $MysqlExe -ErrorAction SilentlyContinue
if ($null -ne $mysqlCmd) {
    $command = "`"$MysqlExe`" -h $Host -P $Port -u $User -p$Password < `"$sqlPath`""
    cmd /c $command

    if ($LASTEXITCODE -eq 0) {
        Write-Output "Database init succeeded (mysql CLI)."
        exit 0
    }
    Write-Warning "mysql CLI failed, trying Python fallback..."
}

if (-not (Get-Command python -ErrorAction SilentlyContinue)) {
    Write-Error "Neither mysql nor python command was found."
    exit 1
}

if (-not (Test-Path $pythonInit)) {
    Write-Error "Python init script not found: $pythonInit"
    exit 1
}

python $pythonInit --host $Host --port $Port --user $User --password $Password --sql-file $sqlPath

if ($LASTEXITCODE -ne 0) {
    Write-Error "Database init failed. Check MySQL service and credentials."
    exit $LASTEXITCODE
}

Write-Output "Database init succeeded (Python)."

