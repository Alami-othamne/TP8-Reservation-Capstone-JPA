# TP Capstone – Application de réservation de salles avec JPA/Hibernate

## Objectif

Ce TP réalise une application Java de réservation de salles, sans Spring. Il rassemble la persistance JPA, les transactions, les recherches et la gestion des conflits, avec des tests et des preuves d'exécution.

## Technologies utilisées

Java **17.0.16**, Maven **3.9.11**, JPA **2.2** (`javax.persistence`), Hibernate Core/JCache **5.4.32.Final**, Ehcache **3.8.1**, H2 **1.4.200**, JUnit Jupiter **5.10.2**.

## Travail réalisé

- Entités `Utilisateur`, `Salle`, `Reservation`, relations LAZY et contraintes SQL.
- CRUD transactionnel, contrôle des disponibilités et intervalles `[début, fin)` ; les réservations adjacentes sont autorisées.
- `@Version` et conflit optimiste réel entre deux EntityManager ; verrou pessimiste sur la salle pour les créations concurrentes.
- Requêtes JPQL paramétrées et filtres dynamiques Criteria API, avec chargement des associations utiles.
- Cache L2 de `Salle`, Query Cache explicite et comparaison des SQL avec/sans cache.
- Jeu reproductible : **10 utilisateurs, 8 salles, 32 réservations**, sans chevauchement.
- Migration SQL v1 → v2 sur disque, historique `schema_migrations`, redémarrage avec `hbm2ddl=validate`.
- [Rapport de performance](rapports/rapport-performance.md), [comparaison du cache](rapports/comparaison-cache.csv) et [compte rendu](rapports/compte-rendu.md).

## Exécution

Dans le terminal intégré de VS Code ou IntelliJ, avec Java 17 sélectionné :

```powershell
cd C:\reservation-capstone
.\scripts\executer-tp.ps1
# Ajouter -Captures pour parcourir les preuves dans le terminal.
```

Le script sélectionne un JDK 17 installé, vérifie Maven, enregistre les journaux et s'arrête en cas d'erreur. Pour un autre poste, fournir `-JavaHome "chemin-du-jdk-17"`.

Commandes Maven équivalentes :

```powershell
mvn clean compile
mvn clean test
mvn clean package
mvn exec:java "-Dexec.mainClass=com.example.util.MigrationDemo" "-Dexec.args=v1"
mvn exec:java "-Dexec.mainClass=com.example.util.MigrationDemo" "-Dexec.args=v2"
mvn exec:java "-Dexec.mainClass=com.example.MainApp"
```

La démonstration de migration utilise `data/migration-proof.mv.db`, distincte de `data/reservations.mv.db`. Exécuter v1 puis v2 pour une nouvelle base témoin ; les passages suivants conservent le schéma v2 et les données. `MainApp` applique les migrations manquantes **avant** d'ouvrir JPA. Les tests utilisent des bases isolées. Le JAR produit nécessite les dépendances Maven ; lancer l'application par `exec:java`.

## Résultats

| Vérification | Résultat observé |
|---|---|
| Compilation | BUILD SUCCESS, Java 17 |
| Tests JUnit | **61 réussis, 0 échec, 0 erreur, 0 ignoré** |
| Exécution MainApp | Terminée ; état final 10 / 8 / 32 |
| Migration SQL | Description absente en v1, présente en v2 ; données conservées, index unique et validation JPA vérifiés |
| Concurrence | 1 commit et 1 refus pour 2 créations incompatibles ; conflit optimiste détecté |
| Cache Hibernate | Test isolé : **1 hit, 1 miss, 1 put**, 1 SQL contre 2 sans cache |
| Rapport de performance | Markdown et CSV générés avec les compteurs réels |
| Packaging | BUILD SUCCESS ; `target/reservation-capstone-1.0-SNAPSHOT.jar` |

## Captures d'exécution

**12 PNG d’extraits de journaux authentiques, pas des screenshots.** Les commandes ont été exécutées dans le terminal intégré VS Code, mais l’accès graphique est devenu indisponible lors de l’enregistrement final. Chaque PNG porte la mention « EXTRAIT DE LOGS », le fichier source et les numéros de ligne. [Le manifeste](captures/manifest.json) conserve les textes et empreintes SHA-256.

Pour régénérer les PNG sous Windows : `.\scripts\generer-captures.ps1`.

1. [Compilation Maven](captures/01-compilation.png) — Java 17 et BUILD SUCCESS.
2. [Initialisation des données](captures/02-initialisation-donnees.png) — effectifs et absence de doublons.
3. [Liste des utilisateurs](captures/03-liste-utilisateurs.png) — dix utilisateurs.
4. [Liste des salles](captures/04-liste-salles.png) — huit salles et leurs capacités.
5. [CRUD des réservations](captures/05-crud-reservations.png) — création, modification, suppression et disponibilités.
6. [Requêtes JPQL](captures/06-requetes-jpql.png) — résultats des recherches.
7. [Criteria API](captures/07-criteria-api.png) — filtres facultatifs et combinés.
8. [Migration SQL](captures/08-migration-sql.png) — v1, v2, conservation et validation.
9. [Verrouillage optimiste et concurrence](captures/09-optimistic-locking.png) — conflits réels.
10. [Cache de second niveau](captures/10-cache-second-niveau.png) — compteurs avec/sans cache.
11. [Rapport de performance](captures/11-rapport-performance.png) — statistiques mesurées.
12. [Tests JUnit et packaging](captures/12-tests-junit-build.png) — 61 tests et JAR généré.

## Conclusion

Ce travail met en pratique une application JPA complète, avec des transactions fiables et des contrôles de concurrence distincts pour les insertions et les mises à jour. Les migrations et les mesures de cache sont vérifiées par des exécutions et des tests reproductibles.
