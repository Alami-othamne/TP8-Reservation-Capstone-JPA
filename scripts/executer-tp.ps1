param([string]$JavaHome, [switch]$Captures)
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
Set-Location -LiteralPath $root
if (-not $JavaHome) {
 $candidates=@($env:JAVA_HOME,'C:\Program Files\Eclipse Adoptium\jdk-17.0.16.8-hotspot','C:\Users\othma\.jdks\ms-17.0.17')
 foreach($candidate in $candidates) {
  if ($candidate -and (Test-Path -LiteralPath "$candidate\release")) {
   if ((Get-Content -LiteralPath "$candidate\release") -match 'JAVA_VERSION="17\.') {$JavaHome=$candidate;break}
  }
 }
}
if (-not $JavaHome -or -not (Test-Path -LiteralPath "$JavaHome\bin\java.exe")) {throw 'JDK 17 introuvable. Fournir -JavaHome.'}
$env:JAVA_HOME=$JavaHome
$env:PATH="$JavaHome\bin;$env:PATH"
$env:MAVEN_OPTS='-Dfile.encoding=UTF-8 -Dorg.jboss.logging.provider=slf4j'
[Console]::OutputEncoding=[Text.UTF8Encoding]::new($false)
$OutputEncoding=[Console]::OutputEncoding
New-Item -ItemType Directory -Path resultats -Force | Out-Null
$maven=(Get-Command mvn.cmd -ErrorAction Stop).Source
function Invoke-Maven([string]$Log,[string[]]$Arguments) {
 Write-Host "`n>>> mvn $($Arguments -join ' ')"
 # Les diagnostics stderr Java sont journalisés ; le code de sortie reste décisif.
 $previousPreference=$ErrorActionPreference
 $ErrorActionPreference='Continue'
 $writer=[IO.StreamWriter]::new((Join-Path $root $Log),$false,[Text.UTF8Encoding]::new($false))
 try { & $maven @Arguments 2>&1 | ForEach-Object { $line="$_"; $writer.WriteLine($line); Write-Host $line } } finally { $writer.Dispose(); $ErrorActionPreference=$previousPreference }
 $code=$LASTEXITCODE
 if($code -ne 0){throw "Maven a échoué ($code) : $Log"}
}
Invoke-Maven 'resultats/00-environnement.txt' @('-version')
if(-not ((Get-Content resultats/00-environnement.txt -Raw) -match 'Java version: 17\.')){throw 'Maven ne tourne pas avec Java 17'}
Invoke-Maven 'resultats/01-compilation.txt' @('-B','-ntp','clean','compile')
Invoke-Maven 'resultats/02-tests-junit.txt' @('-B','-ntp','clean','test')
Invoke-Maven 'resultats/03-package.txt' @('-B','-ntp','clean','package')
# Deux processus JVM distincts : v1 sans description, puis v2 + validate.
# La première preuve est conservée aux exécutions suivantes.
if(-not (Test-Path data/migration-proof.mv.db)){
 Invoke-Maven 'resultats/migration-v1.txt' @('-B','-ntp','exec:java','-Dexec.mainClass=com.example.util.MigrationDemo','-Dexec.args=v1')
 Invoke-Maven 'resultats/migration-v2.txt' @('-B','-ntp','exec:java','-Dexec.mainClass=com.example.util.MigrationDemo','-Dexec.args=v2')
 if(-not (Select-String -Path resultats/migration-v1.txt -Pattern 'PREUVE V1 : description présente=false' -Quiet)){throw 'Preuve v1 absente'}
 if(-not (Select-String -Path resultats/migration-v2.txt -Pattern 'PREUVE V2 : description=true' -Quiet)){throw 'Preuve v2 absente'}
 Get-Content resultats/migration-v1.txt,resultats/migration-v2.txt -Encoding UTF8 | Set-Content resultats/04-migration.txt -Encoding UTF8
}else{
 Invoke-Maven 'resultats/migration-repassage.txt' @('-B','-ntp','exec:java','-Dexec.mainClass=com.example.util.MigrationDemo','-Dexec.args=v2')
}
Invoke-Maven 'resultats/mainapp-complet.txt' @('-B','-ntp','exec:java','-Dexec.mainClass=com.example.MainApp')
& "$PSScriptRoot/extraire-resultats.ps1"
if($Captures){& "$PSScriptRoot/presenter-captures.ps1"}
Write-Host 'TP terminé sans erreur.'
