$projectRoot = Split-Path -Parent $PSScriptRoot
$backendPath = Join-Path $projectRoot "backend"

if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
    Write-Error "Maven (mvn) not found. Install JDK 17+ and Maven 3.9+ first."
    exit 1
}

Set-Location $backendPath
mvn spring-boot:run
