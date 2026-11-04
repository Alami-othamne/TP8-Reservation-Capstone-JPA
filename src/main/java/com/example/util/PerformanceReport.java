package com.example.util;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import javax.persistence.*;
import org.hibernate.*;
import org.hibernate.stat.Statistics;

public class PerformanceReport {
  private final EntityManagerFactory f;
  private final Path directory;
  private final long start = System.nanoTime();

  public PerformanceReport(EntityManagerFactory f, Path directory) {
    this.f = f;
    this.directory = directory;
  }

  public Map<String, Number> metrics() {
    EntityManager em = f.createEntityManager();
    try {
      Session session = em.unwrap(Session.class);
      SessionFactory sf = session.getSessionFactory();
      Statistics s = sf.getStatistics();
      Map<String, Number> m = new LinkedHashMap<>();
      m.put("Requetes HQL/JPQL", s.getQueryExecutionCount());
      m.put("Prepared statements", s.getPrepareStatementCount());
      m.put("Transactions terminees", s.getTransactionCount());
      m.put("Transactions reussies", s.getSuccessfulTransactionCount());
      m.put("Chargements entites", s.getEntityLoadCount());
      m.put("Insertions", s.getEntityInsertCount());
      m.put("Mises a jour", s.getEntityUpdateCount());
      m.put("Suppressions", s.getEntityDeleteCount());
      m.put("L2 hits", s.getSecondLevelCacheHitCount());
      m.put("L2 misses", s.getSecondLevelCacheMissCount());
      m.put("L2 puts", s.getSecondLevelCachePutCount());
      m.put("Query Cache hits", s.getQueryCacheHitCount());
      m.put("Query Cache misses", s.getQueryCacheMissCount());
      m.put("Query Cache puts", s.getQueryCachePutCount());
      m.put("Conflits optimistes", s.getOptimisticFailureCount());
      m.put("Requete la plus lente ms", s.getQueryExecutionMaxTime());
      m.put("Duree scenario ms", (System.nanoTime() - start) / 1_000_000.0);
      return m;
    } finally {
      em.close();
    }
  }

  public Path generateReport() {
    Map<String, Number> m = metrics();
    StringBuilder md =
        new StringBuilder(
            "# Rapport de performance Hibernate\n\n"
                + "Mesures réelles de cette exécution ; ce scénario pédagogique n'est pas un"
                + " benchmark de charge.\n\n"
                + "| Indicateur | Valeur |\n"
                + "|---|---:|\n");
    StringBuilder csv = new StringBuilder("indicateur,valeur\n");
    m.forEach(
        (k, v) -> {
          md.append("| ").append(k).append(" | ").append(v).append(" |\n");
          csv.append(k).append(',').append(v).append('\n');
        });
    md.append(
        "\n"
            + "Périmètre : compteur de la SessionFactory principale, depuis son ouverture (hors"
            + " JDBC de migration et factory sans cache). La durée inclut les démonstrations et"
            + " leurs sorties. Les compteurs sont cumulatifs, sauf les deltas isolés du fichier"
            + " comparaison-cache.csv.\n\n"
            + "Le cache L1 appartient à un EntityManager ; le cache L2 partage les entités Salle"
            + " entre EntityManager. Le Query Cache nécessite le hint org.hibernate.cacheable."
            + " HQL/JPQL compte les requêtes ORM ; Prepared statements inclut aussi find, DML et"
            + " chargements associés.\n");
    try {
      Files.createDirectories(directory);
      Path out = directory.resolve("rapport-performance.md");
      Files.writeString(out, md);
      Files.writeString(directory.resolve("statistiques-hibernate.csv"), csv);
      System.out.print(md);
      return out;
    } catch (IOException ex) {
      throw new IllegalStateException("Écriture du rapport impossible", ex);
    }
  }
}
