$projectRoot = Split-Path -Parent $PSScriptRoot
$frontendPath = Join-Path $projectRoot "frontend"

if (-not (Get-Command npm.cmd -ErrorAction SilentlyContinue)) {
    Write-Error "npm.cmd not found. Install Node.js 18+ first."
    exit 1
}

Set-Location $frontendPath

if (-not (Test-Path (Join-Path $frontendPath "node_modules"))) {
    npm.cmd install
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Frontend dependency install failed."
        exit $LASTEXITCODE
    }
}

npm.cmd run dev
