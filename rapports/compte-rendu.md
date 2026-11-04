# Compte rendu — TP Capstone JPA/Hibernate

## Environnement et périmètre

Projet : `C:\reservation-capstone`. Java Temurin 17.0.16, Maven 3.9.11, Hibernate Core et JCache 5.4.32.Final, Ehcache 3.8.1, H2 1.4.200. Application console classique JPA 2.2, RESOURCE_LOCAL, sans Spring. Les versions résolues sont archivées dans [dependances-maven.txt](../resultats/dependances-maven.txt).

Le PATH initial utilisait Java 8. Le script choisit Java 17 sans modifier les variables système. JAXB, absent du JDK 17, est fourni par Maven (`jaxb-runtime` 2.3.8 et `jaxb-api` 2.3.1). Le verrouillage des versions évite les anciennes dépendances transitives JAXB ; aucune intégration Ehcache 2 n'est utilisée.

## Modèle, règles et transactions

`Utilisateur` et `Salle` exposent chacun une collection inverse de réservations ; les deux ManyToOne de `Reservation` sont obligatoires et LAZY. Les trois entités ont `@Version`. `toString()` n'accède pas aux relations. Les méthodes de consultation des réservations utilisent `join fetch` pour fournir les associations nécessaires sans N+1 à l'affichage.

Les entités contrôlent leurs valeurs par callbacks JPA. La base impose NOT NULL, unicité d'email en v2, clés étrangères, capacité positive et fin après début. Les emails sont normalisés en minuscules par le modèle ; les services refusent les nouveaux créneaux dans le passé. Les tests injectent une horloge fixe, tandis que l'initialisation prend une date future relative au fuseau Africa/Casablanca.

`JpaUtil.tx` ouvre une transaction, effectue le commit ou le rollback actif et ferme toujours l'EntityManager. Les exceptions de contraintes deviennent des exceptions métier avec leur cause conservée ; les conflits optimistes restent identifiables. Les suppressions de salles et d'utilisateurs référencés sont refusées, avec les clés étrangères comme protection supplémentaire.

L'intersection de deux intervalles semi-ouverts est `nouveauDebut < finExistante AND nouveauFin > debutExistant`. Une égalité sur la frontière ne constitue pas un chevauchement. La recherche `between` et les bornes Criteria ont cette même définition. Les filtres nom, capacité et utilisateur sont facultatifs ; les caractères `%` et `_` d'un nom sont échappés comme caractères littéraux.

## Concurrence

Chaque création verrouille la ligne `Salle` en `PESSIMISTIC_WRITE`, puis interroge les chevauchements et insère dans la même transaction. Le niveau d'isolation H2 est READ_COMMITTED. Deux threads synchronisés par latch tentent réellement le même créneau : **1 commit, 1 refus métier et 1 ligne en base**. Un autre test conserve un verrou de salle ouvert et vérifie que la création attend sa libération.

La modification conserve la version de l'entité détachée avec `merge`. Elle verrouille les anciennes/nouvelles salles dans l'ordre de leurs identifiants. Le test avec deux EntityManager charge la même version, valide EM1, puis constate le rejet d'EM2 par Hibernate. La première modification est relue et conservée ; aucune exception optimiste n'est construite artificiellement.

Cette protection suppose que les écritures passent par les services. H2 n'impose pas de contrainte SQL d'exclusion des intervalles : des insertions JDBC externes pourraient contourner le protocole applicatif. Le mode démonstration optimiste utilise volontairement des EntityManager directs sur un créneau isolé.

## Migration réelle

La base témoin persistante `data/migration-proof` est séparée des données de l'application. Un premier processus crée uniquement v1 et insère un utilisateur, une salle et une réservation ; les métadonnées JDBC confirment l'absence de `description`. Après fermeture de cette connexion, un second processus applique v2, vérifie les valeurs et les effectifs conservés, l'index email unique, puis démarre JPA avec `validate`.

`MigrationExecutor` lit les scripts du classpath et utilise le parseur H2 `RunScript` pour exécuter les instructions SQL séparément. L'historique n'est enregistré qu'après succès. Les DDL H2 peuvent committer implicitement : le mécanisme ne promet donc pas un rollback atomique du schéma. Les instructions idempotentes permettent une reprise contrôlée après correction de la cause. Le test de reprise introduit des emails dupliqués, constate l'échec de l'index après ajout de colonne, corrige les données puis termine la migration.

Une contrainte unique déjà présente sur le seul champ email est reconnue via les métadonnées ; aucun index équivalent redondant n'est ajouté. Les migrations sont exécutées par un seul processus d'administration à la fois. MySQL n'a pas été installé ni testé : les scripts livrés ciblent H2, sans prétendre fournir une variante MySQL validée.

Preuves : [journal de migration](../resultats/04-migration.txt), [tests](../resultats/02-tests-junit.txt).

## Cache et performances

Salle utilise READ_WRITE via JCache/Ehcache 3. Les trois régions sont déclarées dans `ehcache.xml`, sans expiration des timestamps. Les tests de factories cachées sont séquentiels ; l'application conserve une seule factory cachée active pour l'URI JCache.

Le test isolé évince le cache puis charge deux fois la même salle dans deux EntityManager distincts. Deltas observés : **1 hit, 1 miss, 1 put, 1 prepared statement**. Sans cache : **2 prepared statements**, aucun hit. Les durées sont respectivement **4.8185 ms** et **6.3999 ms**. Une si petite mesure, sans échauffement statistique, ne démontre pas un gain de temps ; elle démontre l'économie d'une requête SQL.

Le cache L1 est propre à chaque EntityManager. Le L2 est partagé au sein de la factory. Le Query Cache nécessite le hint `org.hibernate.cacheable` et produit un hit dans la démonstration ; une requête JPQL sans hint est également testée.

La dernière exécution principale mesure **27 requêtes HQL/JPQL**, **54 prepared statements**, **12 transactions terminées**, dont **9 réussies**, en **3072.8228 ms**. Compteurs L2 cumulés : **9 hits, 1 misses, 16 puts**. Ces valeurs cumulées ne sont pas les deltas du test isolé. La base de démonstration avait déjà été initialisée : le rapport de la relance mesure notamment les insertions temporaires du scénario, et non une nouvelle insertion des 32 réservations.

Les statistiques concernent la factory principale et excluent le JDBC des migrations et la factory sans cache. Les SELECT par `find` et les DML figurent dans les prepared statements, pas tous dans les requêtes HQL/JPQL. La durée inclut les sorties console et les scénarios. Les mesures sont celles du poste de TP, sans conclusion de performance sous charge.

## Vérifications et remise

**61 tests réussis**, zéro échec, zéro erreur, zéro test ignoré : 51 tests applicatifs et 10 tests de migration. Compilation, packaging et MainApp terminent en BUILD SUCCESS. L'état final conserve **10 utilisateurs, 8 salles et 32 réservations** après suppression des réservations temporaires de démonstration.

Les 12 captures livrées sont des PNG d’extraits de journaux, explicitement étiquetés comme tels, et non des screenshots. Les commandes ont bien été exécutées dans le terminal intégré VS Code ; l’accès graphique est ensuite devenu indisponible (canal natif introuvable, y compris après réinitialisation). Le repli demandé dans la mission a donc été utilisé : `generer-captures.ps1` rend les lignes réelles des logs, avec leurs numéros et leurs empreintes dans `captures/manifest.json`. Le README utilise des liens Markdown relatifs cliquables. Le premier démarrage et le jeu initial sont conservés dans `resultats/initialisation-premier-demarrage.txt` ; les autres journaux présentent la dernière exécution validée. Les journaux de tests contiennent un diagnostic SQL attendu lors du test de rejet d'email dupliqué ; le bilan Surefire confirme le succès de ce test.

Les sources et scripts sont prêts à être versionnés. Les bases H2, `target`, les fichiers IDE et les secrets sont ignorés. Aucun ancien projet n'a été modifié ; aucun dépôt distant, commit ou push n'a été créé.

## Références

- [Documentation officielle Hibernate ORM 5.4](https://hibernate.org/orm/releases/5.4/).
- [Configuration XML Ehcache 3.8](https://www.ehcache.org/documentation/3.8/xsds.html).
- [API Hibernate Statistics](https://docs.jboss.org/hibernate/orm/5.4/javadocs/org/hibernate/stat/Statistics.html).
