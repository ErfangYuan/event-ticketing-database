param([switch]$Dev, [switch]$SkipBuild, [switch]$Check)
$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
$apiProcess = $null
$frontProcess = $null
$prompted = $false
$generatedSecret = $false
try {
    if (-not $env:MYTIX_DB_NAME -or -not $env:MYTIX_DB_USER) { throw 'Set MYTIX_DB_NAME and MYTIX_DB_USER to your selected project database first. See README.md.' }
    $nodeVersion = & node --version
    if ($LASTEXITCODE -ne 0 -or [int]($nodeVersion.TrimStart('v').Split('.')[0]) -lt 22) { throw 'Node.js 22+ is required.' }
    $java = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/java.exe' } else { (Get-Command java).Source }
    if (-not $SkipBuild) {
        & ./mvnw.cmd -q compile dependency:copy-dependencies
        if ($LASTEXITCODE -ne 0) { throw 'Java build failed.' }
        Push-Location web
        try {
            & npm.cmd ci --no-fund
            if ($LASTEXITCODE -ne 0) { throw 'Frontend dependency installation failed.' }
            if (-not $Dev) { & npm.cmd run build; if ($LASTEXITCODE -ne 0) { throw 'Frontend build failed.' } }
        } finally { Pop-Location }
    }
    if (-not $env:MYTIX_DB_PASSWORD) {
        $secure = Read-Host 'MySQL password (local input only)' -AsSecureString
        $credential = New-Object System.Management.Automation.PSCredential('unused', $secure)
        $env:MYTIX_DB_PASSWORD = $credential.GetNetworkCredential().Password
        $prompted = $true
    }
    if (-not $env:MYTIX_API_SECRET) {
        $bytes = New-Object byte[] 32
        $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
        try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
        $env:MYTIX_API_SECRET = [Convert]::ToBase64String($bytes)
        $generatedSecret = $true
    }
    if (-not $env:MYTIX_API_PORT) { $env:MYTIX_API_PORT = '8081' }
    if (-not $env:MYTIX_WEB_PORT) { $env:MYTIX_WEB_PORT = '3000' }
    if (-not $env:MYTIX_DEMO_MODE) { $env:MYTIX_DEMO_MODE = 'true' }
    $env:MYTIX_API_URL = 'http://127.0.0.1:' + $env:MYTIX_API_PORT
    if (-not $env:MYTIX_WEB_ORIGIN) { $env:MYTIX_WEB_ORIGIN = 'http://127.0.0.1:' + $env:MYTIX_WEB_PORT }
    foreach ($port in @([int]$env:MYTIX_API_PORT,[int]$env:MYTIX_WEB_PORT)) {
        $probe = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback,$port)
        try { $probe.Start() } catch { throw "Port $port is already in use. Stop the existing instance or choose another port." } finally { $probe.Stop() }
    }
    $logs = Join-Path $PSScriptRoot 'local/web-runtime'
    New-Item -ItemType Directory -Force -Path $logs | Out-Null
    $apiProcess = Start-Process -FilePath $java -ArgumentList @('-cp','"target/classes;target/dependency/*"','mytix.api.WebServer') -WorkingDirectory $PSScriptRoot -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $logs 'api-out.log') -RedirectStandardError (Join-Path $logs 'api-error.log')
    $ready = $false
    for ($attempt=0; $attempt -lt 50; $attempt++) {
        if ($apiProcess.HasExited) { throw "Java API exited. Inspect $logs\api-error.log." }
        try {
            $null = Invoke-RestMethod -Uri ($env:MYTIX_API_URL+'/api/status') -Method Post -ContentType 'application/json' -Headers @{'X-Mytix-Key'=$env:MYTIX_API_SECRET} -Body '{}' -TimeoutSec 2
            $ready = $true; break
        } catch { Start-Sleep -Milliseconds 200 }
    }
    if (-not $ready) { throw 'The Java API did not become ready.' }
    Write-Host "Open $env:MYTIX_WEB_ORIGIN - Ctrl+C stops both services. Demo maintenance: $env:MYTIX_DEMO_MODE."
    # Only the Java child needs database credentials; Next.js receives the project API secret.
    $databaseEnvironment = @{}
    Get-ChildItem Env:MYTIX_DB_* | ForEach-Object { $databaseEnvironment[$_.Name]=$_.Value; Remove-Item -LiteralPath ('Env:'+ $_.Name) }
    try {
        Push-Location web
        try {
            $mode = if ($Dev) { 'dev' } else { 'start' }
            if ($Check) {
                $frontProcess = Start-Process -FilePath (Get-Command node).Source -ArgumentList @('node_modules/next/dist/bin/next',$mode,'--hostname','127.0.0.1','--port',$env:MYTIX_WEB_PORT) -WorkingDirectory (Get-Location).Path -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $logs 'front-out.log') -RedirectStandardError (Join-Path $logs 'front-error.log')
                $frontReady=$false
                for($attempt=0;$attempt -lt 50;$attempt++) {
                    if($frontProcess.HasExited){throw 'Frontend exited during the startup check.'}
                    try {
                        $result=Invoke-RestMethod -Uri ($env:MYTIX_WEB_ORIGIN+'/api/status') -Method Post -ContentType 'application/json' -Headers @{Origin=$env:MYTIX_WEB_ORIGIN} -Body '{}' -TimeoutSec 2
                        if(@($result.database.counts.PSObject.Properties).Count -eq 22){$frontReady=$true;break}
                    } catch { Start-Sleep -Milliseconds 200 }
                }
                if(-not $frontReady){throw 'Browser-facing API did not become ready.'}
                Write-Host 'PASS: MyTix Web startup check, both services connected to all 22 project tables.'
            } else {
                & node node_modules/next/dist/bin/next $mode --hostname 127.0.0.1 --port $env:MYTIX_WEB_PORT
                if ($LASTEXITCODE -ne 0) { throw 'Frontend exited unsuccessfully.' }
            }
        } finally { Pop-Location }
    } finally { foreach ($name in $databaseEnvironment.Keys) { Set-Item -LiteralPath ('Env:'+ $name) -Value $databaseEnvironment[$name] } }
} finally {
    if ($frontProcess -and -not $frontProcess.HasExited) { Stop-Process -Id $frontProcess.Id -ErrorAction SilentlyContinue }
    if ($apiProcess -and -not $apiProcess.HasExited) { Stop-Process -Id $apiProcess.Id -ErrorAction SilentlyContinue }
    if ($prompted) { Remove-Item Env:MYTIX_DB_PASSWORD -ErrorAction SilentlyContinue }
    if ($generatedSecret) { Remove-Item Env:MYTIX_API_SECRET -ErrorAction SilentlyContinue }
    Pop-Location
}
