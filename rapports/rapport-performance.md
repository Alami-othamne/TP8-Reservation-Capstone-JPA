# Rapport de performance Hibernate

Mesures réelles de cette exécution ; ce scénario pédagogique n'est pas un benchmark de charge.

| Indicateur | Valeur |
|---|---:|
| Requetes HQL/JPQL | 27 |
| Prepared statements | 54 |
| Transactions terminees | 12 |
| Transactions reussies | 9 |
| Chargements entites | 214 |
| Insertions | 2 |
| Mises a jour | 2 |
| Suppressions | 2 |
| L2 hits | 9 |
| L2 misses | 1 |
| L2 puts | 16 |
| Query Cache hits | 1 |
| Query Cache misses | 1 |
| Query Cache puts | 1 |
| Conflits optimistes | 1 |
| Requete la plus lente ms | 760 |
| Duree scenario ms | 3072.8228 |

Périmètre : compteur de la SessionFactory principale, depuis son ouverture (hors JDBC de migration et factory sans cache). La durée inclut les démonstrations et leurs sorties. Les compteurs sont cumulatifs, sauf les deltas isolés du fichier comparaison-cache.csv.

Le cache L1 appartient à un EntityManager ; le cache L2 partage les entités Salle entre EntityManager. Le Query Cache nécessite le hint org.hibernate.cacheable. HQL/JPQL compte les requêtes ORM ; Prepared statements inclut aussi find, DML et chargements associés.
