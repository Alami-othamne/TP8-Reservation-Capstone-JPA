# Rendu des journaux authentiques, sans interface graphique ni resultat invente.
# Windows PowerShell 5.1 / .NET System.Drawing : aucune dependance Python.
$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.Drawing
$root=Split-Path $PSScriptRoot -Parent
Set-Location -LiteralPath $root
New-Item -ItemType Directory -Path captures -Force | Out-Null
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

$font=[Drawing.Font]::new('Consolas',20,[Drawing.FontStyle]::Regular,[Drawing.GraphicsUnit]::Pixel)
$small=[Drawing.Font]::new('Consolas',16,[Drawing.FontStyle]::Regular,[Drawing.GraphicsUnit]::Pixel)
$title=[Drawing.Font]::new('Segoe UI',27,[Drawing.FontStyle]::Bold,[Drawing.GraphicsUnit]::Pixel)
$ink=[Drawing.SolidBrush]::new([Drawing.Color]::FromArgb(31,41,55))
$muted=[Drawing.SolidBrush]::new([Drawing.Color]::FromArgb(80,95,115))
$blue=[Drawing.SolidBrush]::new([Drawing.Color]::FromArgb(17,75,130))
$manifest=@()
try {
 foreach($page in $pages){
  $source=Join-Path $root "resultats/$($page[2])"
  $all=Get-Content -LiteralPath $source -Encoding UTF8
  $selected=@()
  for($i=0;$i -lt $all.Count;$i++){
   if($all[$i] -match $page[3]){$selected+=@{line=$i+1;text=[string]::Copy($all[$i])}}
  }
  if($selected.Count -eq 0){throw "Aucune preuve pour $($page[0])"}
  $rendered=[Collections.Generic.List[string]]::new()
  foreach($item in $selected){
   $prefix=('{0,4} | ' -f $item.line)
   $text=$item.text
   while($text.Length -gt 105){$rendered.Add($prefix+$text.Substring(0,105));$text=$text.Substring(105);$prefix='     | '}
   $rendered.Add($prefix+$text)
  }
  $height=[Math]::Max(430,230+($rendered.Count*30))
  $bitmap=[Drawing.Bitmap]::new(1480,$height)
  $g=[Drawing.Graphics]::FromImage($bitmap)
  try{
   $g.Clear([Drawing.Color]::FromArgb(248,250,252))
   $g.TextRenderingHint=[Drawing.Text.TextRenderingHint]::AntiAliasGridFit
   $g.FillRectangle($blue,0,0,1480,8)
   $g.DrawString('EXTRAIT DE LOGS - PAS UNE CAPTURE D''ECRAN',$title,$blue,36,28)
   $g.DrawString("$($page[0]) | $($page[1])",$font,$ink,36,78)
   $g.DrawString("Source : resultats/$($page[2])",$small,$muted,36,117)
   $g.DrawString('Lignes originales numerotees ; retours a la ligne adaptes pour la lecture.',$small,$muted,36,144)
   $y=190
   foreach($line in $rendered){$g.DrawString($line,$font,$ink,36,$y);$y+=30}
   $g.DrawString('TP Capstone | Java 17 - JPA 2.2 - Hibernate 5.4.32.Final - H2',$small,$muted,36,($height-32))
   $file=Join-Path $root "captures/$($page[0]).png"
   $bitmap.Save($file,[Drawing.Imaging.ImageFormat]::Png)
  }finally{$g.Dispose();$bitmap.Dispose()}
  $manifest+=@{file="captures/$($page[0]).png";source="resultats/$($page[2])";source_sha256=(Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash;image_sha256=(Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash;lines=$selected;kind='extrait de logs, pas screenshot'}
  Write-Host "PNG verifie : $($page[0]) ($($selected.Count) lignes originales)"
 }
 $manifest | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath captures/manifest.json -Encoding UTF8
}finally{$font.Dispose();$small.Dispose();$title.Dispose();$ink.Dispose();$muted.Dispose();$blue.Dispose()}
