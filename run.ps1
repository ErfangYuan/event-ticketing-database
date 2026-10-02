$ErrorActionPreference='Stop'
Push-Location $PSScriptRoot
$prompted=$false
try {
 if ($env:JAVA_HOME -and -not (Test-Path (Join-Path $env:JAVA_HOME 'bin/javac.exe'))) {
  throw 'JAVA_HOME must point to a JDK 17+ directory containing bin/javac.exe, not its parent directory.'
 }
 & ./mvnw.cmd -q compile dependency:copy-dependencies
 if ($LASTEXITCODE -ne 0) { throw 'Dependency download or compilation failed.' }
 if (-not $env:MYTIX_DB_PASSWORD) {
  $secure=Read-Host 'MySQL password (local input only)' -AsSecureString
  $credential=New-Object System.Management.Automation.PSCredential('unused',$secure)
  $env:MYTIX_DB_PASSWORD=$credential.GetNetworkCredential().Password
  $prompted=$true
 }
 $java = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/java.exe' } else { 'java' }
 & $java -cp 'target/classes;target/dependency/*' mytix.Main
 if ($LASTEXITCODE -ne 0) { throw 'MyTix exited unsuccessfully.' }
} finally { if ($prompted) { Remove-Item Env:MYTIX_DB_PASSWORD -ErrorAction SilentlyContinue }; Pop-Location }
