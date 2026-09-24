$ErrorActionPreference='Stop'
Push-Location $PSScriptRoot
$prompted=$false
try {
 if (-not $env:MYTIX_DB_PASSWORD) {
  $secure=Read-Host 'MySQL password (local input only)' -AsSecureString
  $credential=New-Object System.Management.Automation.PSCredential('unused',$secure)
  $env:MYTIX_DB_PASSWORD=$credential.GetNetworkCredential().Password
  $prompted=$true
 }
 mvn -q compile dependency:copy-dependencies
 if ($LASTEXITCODE -ne 0) { throw 'Dependency download or compilation failed.' }
 java -cp 'target/classes;target/dependency/*' mytix.Main
 if ($LASTEXITCODE -ne 0) { throw 'MyTix exited unsuccessfully.' }
} finally { if ($prompted) { Remove-Item Env:MYTIX_DB_PASSWORD -ErrorAction SilentlyContinue }; Pop-Location }
