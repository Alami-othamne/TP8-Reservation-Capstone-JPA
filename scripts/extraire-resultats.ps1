$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
$source=Join-Path $root 'resultats/mainapp-complet.txt'
$lines=Get-Content -LiteralPath $source -Encoding UTF8
$sections=@{}
$current=''
foreach($line in $lines){
 if($line -match '^=== (.+) ===$'){$current=$Matches[1];$sections[$current]=[Collections.Generic.List[string]]::new()}
 if($current){$sections[$current].Add($line)}
}
$mapping=[ordered]@{
 '05-initialisation-donnees.txt'=@('INITIALISATION DES DONNEES','LISTE DES UTILISATEURS','LISTE DES SALLES','LISTE DES RESERVATIONS')
 '06-crud-reservations.txt'=@('CREATION DE RESERVATION','MODIFICATION DE RESERVATION','SUPPRESSION DE RESERVATION','VERIFICATION DES DISPONIBILITES')
 '07-jpql-criteria.txt'=@('REQUETES JPQL','CRITERIA API')
 '08-concurrence.txt'=@('TEST VERROUILLAGE OPTIMISTE','TEST CREATIONS CONCURRENTES')
 '09-cache-hibernate.txt'=@('TEST CACHE SECOND NIVEAU')
 '10-rapport-performance.txt'=@('STATISTIQUES HIBERNATE','RAPPORT DE PERFORMANCE')
}
foreach($file in $mapping.Keys){
 $content=@('Extraits exacts de resultats/mainapp-complet.txt ; SQL conservé.')
 foreach($section in $mapping[$file]){if(-not $sections.ContainsKey($section)){throw "Section manquante : $section"};$content+=$sections[$section]}
 $content | Set-Content -LiteralPath (Join-Path $root "resultats/$file") -Encoding UTF8
}
