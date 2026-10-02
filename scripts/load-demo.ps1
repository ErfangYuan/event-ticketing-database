param(
    [Parameter(Mandatory=$true)][string]$Database,
    [switch]$ClearOnly
)
$ErrorActionPreference = 'Stop'
Push-Location (Split-Path -Parent $PSScriptRoot)
$prompted = $false
try {
    if ($env:JAVA_HOME -and -not (Test-Path (Join-Path $env:JAVA_HOME 'bin/javac.exe'))) {
        throw 'JAVA_HOME must point to a JDK 17+ containing bin/javac.exe.'
    }
    if ($env:MYTIX_DB_NAME -and $env:MYTIX_DB_NAME -cne $Database) {
        throw 'Database argument must match MYTIX_DB_NAME. No data was changed.'
    }
    $env:MYTIX_DB_NAME = $Database
    & ./mvnw.cmd -q compile dependency:copy-dependencies
    if ($LASTEXITCODE -ne 0) { throw 'Dependency download or compilation failed.' }
    if (-not $env:MYTIX_DB_PASSWORD) {
        $secure = Read-Host 'MySQL password (local input only)' -AsSecureString
        $credential = New-Object System.Management.Automation.PSCredential('unused', $secure)
        $env:MYTIX_DB_PASSWORD = $credential.GetNetworkCredential().Password
        $prompted = $true
    }
    $java = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/java.exe' } else { 'java' }
    $mode = if ($ClearOnly) { '--clear' } else { '--reset-and-load' }
    & $java -cp 'target/classes;target/dependency/*' mytix.database.demo.DemoDataMain $mode $Database
    if ($LASTEXITCODE -ne 0) { throw 'Demo maintenance failed; inspect the message above.' }
} finally {
    if ($prompted) { Remove-Item Env:MYTIX_DB_PASSWORD -ErrorAction SilentlyContinue }
    Pop-Location
}
