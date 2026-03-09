param(
    [switch]$InitDb
)

if ($InitDb) {
    & (Join-Path $PSScriptRoot "init_db.ps1")
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }
}

Start-Process powershell -ArgumentList "-NoExit", "-ExecutionPolicy", "Bypass", "-File", (Join-Path $PSScriptRoot "start_backend.ps1")
Start-Process powershell -ArgumentList "-NoExit", "-ExecutionPolicy", "Bypass", "-File", (Join-Path $PSScriptRoot "start_frontend.ps1")

Write-Output "Backend and frontend windows have been started."
Write-Output "After backend is ready, run: python .\\scripts\\smoke_test.py"
