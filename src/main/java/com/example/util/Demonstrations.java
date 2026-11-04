package com.example.util;

import com.example.model.*;
import com.example.service.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import javax.persistence.*;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;

public final class Demonstrations {
  private Demonstrations() {}

  public static void optimistic(EntityManagerFactory f, Long id) {
    EntityManager a = f.createEntityManager(), b = f.createEntityManager();
    try {
      a.getTransaction().begin();
      b.getTransaction().begin();
      Reservation first = a.find(Reservation.class, id), stale = b.find(Reservation.class, id);
      System.out.println(
          "Versions initiales : EM1=" + first.getVersion() + ", EM2=" + stale.getVersion());
      LocalDateTime expected = first.getDateFin().plusMinutes(5);
      first.setDateFin(expected);
      a.getTransaction().commit();
      System.out.println("EM1 commit : version=" + first.getVersion());
      stale.setDateFin(expected.plusMinutes(5));
      boolean conflict = false;
      try {
        b.getTransaction().commit();
      } catch (RuntimeException ex) {
        for (Throwable c = ex; c != null; c = c.getCause())
          if (c instanceof OptimisticLockException) conflict = true;
        if (!conflict) throw ex;
        if (b.getTransaction().isActive()) b.getTransaction().rollback();
        System.out.println(
            "Conflit optimiste réel détecté ; EM2 rollback effectué, transaction active="
                + b.getTransaction().isActive());
      }
      if (!conflict) throw new IllegalStateException("Conflit optimiste attendu");
      LocalDateTime actual = JpaUtil.read(f, em -> em.find(Reservation.class, id).getDateFin());
      if (!expected.equals(actual)) throw new IllegalStateException("Première modification perdue");
      System.out.println("Première modification conservée : " + actual);
    } finally {
      if (a.getTransaction().isActive()) a.getTransaction().rollback();
      if (b.getTransaction().isActive()) b.getTransaction().rollback();
      a.close();
      b.close();
    }
  }

  public static int concurrent(EntityManagerFactory f, Long user, Long room, LocalDateTime d)
      throws Exception {
    ExecutorService pool = Executors.newFixedThreadPool(2);
    CountDownLatch ready = new CountDownLatch(2), go = new CountDownLatch(1);
    try {
      Callable<Boolean> task =
          () -> {
            ready.countDown();
            if (!go.await(10, TimeUnit.SECONDS))
              throw new IllegalStateException("Barrière expirée");
            try {
              new ReservationService(f).create(user, room, d, d.plusHours(1));
              return true;
            } catch (BusinessException ex) {
              if (!ex.getMessage().startsWith("Chevauchement")) throw ex;
              return false;
            }
          };
      Future<Boolean> a = pool.submit(task), b = pool.submit(task);
      if (!ready.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Threads non prêts");
      go.countDown();
      int successes = (a.get(20, TimeUnit.SECONDS) ? 1 : 0) + (b.get(20, TimeUnit.SECONDS) ? 1 : 0);
      long count =
          JpaUtil.read(
              f,
              em ->
                  em.createQuery(
                          "select count(r) from Reservation r where r.salle.id=:s and"
                              + " r.dateDebut=:d",
                          Long.class)
                      .setParameter("s", room)
                      .setParameter("d", d)
                      .getSingleResult());
      if (successes != 1 || count != 1)
        throw new IllegalStateException("Protection concurrente défaillante");
      System.out.println(
          "2 créations simultanées : commits="
              + successes
              + ", refus de chevauchement="
              + (2 - successes)
              + ", lignes enregistrées="
              + count);
      return successes;
    } finally {
      go.countDown();
      pool.shutdownNow();
      if (!pool.awaitTermination(10, TimeUnit.SECONDS))
        throw new IllegalStateException("Threads non terminés");
    }
  }

  public record CacheSample(long hits, long misses, long puts, long sql, double ms) {}

  public static CacheSample sample(EntityManagerFactory f, Long room) {
    f.getCache().evictAll();
    Statistics s = f.unwrap(SessionFactory.class).getStatistics();
    long h = s.getSecondLevelCacheHitCount(),
        m = s.getSecondLevelCacheMissCount(),
        p = s.getSecondLevelCachePutCount(),
        q = s.getPrepareStatementCount(),
        start = System.nanoTime();
    for (int i = 0; i < 2; i++) JpaUtil.tx(f, em -> em.find(Salle.class, room));
    return new CacheSample(
        s.getSecondLevelCacheHitCount() - h,
        s.getSecondLevelCacheMissCount() - m,
        s.getSecondLevelCachePutCount() - p,
        s.getPrepareStatementCount() - q,
        (System.nanoTime() - start) / 1_000_000.0);
  }

  public static void cache(EntityManagerFactory f, String url, Long room) throws Exception {
    CacheSample on = sample(f, room);
    EntityManagerFactory other = JpaUtil.open(url, false, false);
    CacheSample off;
    try {
      off = sample(other, room);
    } finally {
      other.close();
    }
    if (on.hits() < 1 || on.sql() != 1 || off.sql() != 2)
      throw new IllegalStateException("Comparaison cache inattendue");
    System.out.println("Cache L2 actif, 2 EntityManager distincts : " + on);
    System.out.println("Cache désactivé, 2 EntityManager distincts : " + off);
    Statistics st = f.unwrap(SessionFactory.class).getStatistics();
    long before = st.getQueryCacheHitCount();
    SalleService service = new SalleService(f);
    service.cachedList();
    service.cachedList();
    System.out.println(
        "Query Cache (hint explicite) : hits supplémentaires="
            + (st.getQueryCacheHitCount() - before));
    Files.createDirectories(Path.of("rapports"));
    Files.writeString(
        Path.of("rapports/comparaison-cache.csv"),
        "mode,hits,misses,puts,prepared_statements,duree_ms\nactif,"
            + on.hits()
            + ","
            + on.misses()
            + ","
            + on.puts()
            + ","
            + on.sql()
            + ","
            + on.ms()
            + "\ndesactive,"
            + off.hits()
            + ","
            + off.misses()
            + ","
            + off.puts()
            + ","
            + off.sql()
            + ","
            + off.ms()
            + "\n");
  }
}
