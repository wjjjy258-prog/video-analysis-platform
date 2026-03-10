$projectRoot = Split-Path -Parent $PSScriptRoot
$frontendPath = Join-Path $projectRoot "frontend"

function Resolve-NodePath {
    $nodeCmd = Get-Command node -ErrorAction SilentlyContinue
    if ($nodeCmd -and $nodeCmd.Source) {
        return $nodeCmd.Source
    }
    try {
        $whereNode = (& where.exe node 2>$null | Select-Object -First 1)
        if (-not [string]::IsNullOrWhiteSpace($whereNode)) {
            return $whereNode.Trim()
        }
    } catch {
    }
    return $null
}

function Resolve-NpmPath {
    $npmCmd = Get-Command npm.cmd -ErrorAction SilentlyContinue
    if ($npmCmd -and $npmCmd.Source) {
        try {
            & $npmCmd.Source --version *> $null
            if ($LASTEXITCODE -eq 0) {
                return $npmCmd.Source
            }
        } catch {
        }
    }

    try {
        $whereNpm = (& where.exe npm.cmd 2>$null | Select-Object -First 1)
        if (-not [string]::IsNullOrWhiteSpace($whereNpm)) {
            $candidate = $whereNpm.Trim()
            & cmd /c "`"$candidate`" --version" *> $null
            if ($LASTEXITCODE -eq 0) {
                return $candidate
            }
        }
    } catch {
    }

    return $null
}

Set-Location $frontendPath

if (-not (Test-Path (Join-Path $frontendPath "package.json"))) {
    Write-Error "package.json not found in frontend directory."
    exit 1
}

$nodePath = Resolve-NodePath
if ([string]::IsNullOrWhiteSpace($nodePath)) {
    Write-Error "Node.js not found. Install Node.js 18+ first."
    exit 1
}

$npmPath = Resolve-NpmPath
$viteCli = Join-Path $frontendPath "node_modules\\vite\\bin\\vite.js"

if (-not (Test-Path (Join-Path $frontendPath "node_modules"))) {
    if ([string]::IsNullOrWhiteSpace($npmPath)) {
        Write-Error "npm is unavailable and node_modules is missing. Please reinstall Node.js (with npm) and run frontend install once."
        exit 1
    }

    & $npmPath install
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Frontend dependency install failed."
        exit $LASTEXITCODE
    }
}

# Force long-running crawler/import requests to avoid accidental 10s timeout from environment overrides.
$env:VITE_API_TIMEOUT_MS = "0"

if (-not [string]::IsNullOrWhiteSpace($npmPath)) {
    & $npmPath run dev
    exit $LASTEXITCODE
}

if (-not (Test-Path $viteCli)) {
    Write-Error "Vite CLI not found at $viteCli. Please run npm install once."
    exit 1
}

& $nodePath $viteCli
exit $LASTEXITCODE
