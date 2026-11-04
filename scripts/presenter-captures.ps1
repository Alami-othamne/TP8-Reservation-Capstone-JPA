# À exécuter dans le terminal intégré VS Code. Affiche uniquement de vraies lignes des logs.
$ErrorActionPreference='Stop'
Set-Location -LiteralPath (Split-Path $PSScriptRoot -Parent)
$pages=@(
 @('01-compilation','Compilation Maven','01-compilation.txt','Compiling |BUILD SUCCESS|Total time:'),
 @('02-initialisation-donnees','Initialisation','mainapp-complet.txt','^=== INITIALISATION|^Utilisateurs=|^Jeu existant|^État final'),
 @('03-liste-utilisateurs','Utilisateurs','mainapp-complet.txt','^Utilisateur\['),
 @('04-liste-salles','Salles','mainapp-complet.txt','^Salle\['),
 @('05-crud-reservations','CRUD et disponibilites','mainapp-complet.txt','^Créée :|^Modifiée :|^Suppression vérifiée|^Salles libres=|^Refus attendu'),
 @('06-requetes-jpql','Requetes JPQL','mainapp-complet.txt','^=== REQUETES JPQL|^Par salle='),
 @('07-criteria-api','Criteria API','mainapp-complet.txt','^=== CRITERIA API|^Aucun filtre=|^Nom salle=|^Filtres combinés=|^Aucun résultat='),
 @('08-migration-sql','Migration v1 vers v2 - deux processus','04-migration.txt','^PREUVE|^Connexion v1|^Avant migration|^Témoin|^JPA redémarrée|^Deuxième passage|^Migration v'),
 @('09-optimistic-locking','Concurrence reelle','mainapp-complet.txt','^Versions initiales|^EM1 commit|^Conflit optimiste|^Première modification|^2 créations simultanées'),
 @('10-cache-second-niveau','Cache L2 - EntityManager distincts','mainapp-complet.txt','^Cache L2 actif|^Cache désactivé|^Query Cache \('),
 @('11-rapport-performance','Statistiques Hibernate','mainapp-complet.txt','^Requetes HQL/JPQL=|^Prepared statements=|^Transactions |^Chargements entites=|^Insertions=|^Mises a jour=|^Suppressions=|^L2 |^Query Cache hits=|^Query Cache misses=|^Duree scenario ms=|^Rapport enregistré'),
 @('12-tests-junit-build','Tests JUnit et packaging','03-package.txt','Tests run:|BUILD SUCCESS|Building jar:')
)
foreach($page in $pages){
 Clear-Host
 Write-Host 'TP CAPSTONE - RESERVATION DE SALLES | Java 17 / Hibernate 5.4'
 Write-Host "CAPTURE $($page[0]) - $($page[1])"
 Write-Host "Source : C:\reservation-capstone\resultats\$($page[2])"
 Write-Host 'Extraits authentiques des commandes executees dans ce terminal.'
 Write-Host ('-'*90)
 $found=Get-Content -LiteralPath "resultats/$($page[2])" -Encoding UTF8 | Where-Object {$_ -match $page[3]}
 if(-not $found){throw "Aucune preuve pour $($page[0])"}
 $found | ForEach-Object {Write-Host $_}
 Write-Host ('-'*90)
 Read-Host 'Entree : capture suivante' | Out-Null
}
