package com.example;

import static org.junit.jupiter.api.Assertions.*;

import com.example.model.*;
import com.example.service.*;
import com.example.util.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import javax.persistence.*;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class CapstoneTest {
  EntityManagerFactory f;
  String url;
  UtilisateurService us;
  SalleService ss;
  ReservationService rs;
  Utilisateur u;
  Salle s;
  final LocalDateTime d = LocalDateTime.of(2090, 1, 10, 9, 0);
  @TempDir Path temp;

  @BeforeEach
  void setup() {
    url = "jdbc:h2:mem:test" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000";
    new MigrationExecutor(url).migrate();
    f = JpaUtil.open(url, true, false);
    us = new UtilisateurService(f);
    ss = new SalleService(f);
    rs =
        new ReservationService(
            f, Clock.fixed(Instant.parse("2089-01-01T00:00:00Z"), ZoneOffset.UTC));
    u = us.create("Alice", "alice@example.test");
    s = ss.create("Salle A", 20);
  }

  @AfterEach
  void cleanup() throws Exception {
    if (f != null && f.isOpen()) f.close();
    try (var c = new MigrationExecutor(url).connect();
        var st = c.createStatement()) {
      st.execute("SHUTDOWN");
    }
  }

  Reservation reserve() {
    return rs.create(u.getId(), s.getId(), d, d.plusHours(1));
  }

  List<Long> ids(List<Reservation> values) {
    return values.stream().map(Reservation::getId).toList();
  }

  @Test
  void creationUtilisateur() {
    assertNotNull(u.getId());
    assertEquals("alice@example.test", us.find(u.getId()).orElseThrow().getEmail());
  }

  @Test
  void emailUnique() {
    assertThrows(BusinessException.class, () -> us.create("Autre", "ALICE@example.test"));
    assertEquals(1, us.list().size());
  }

  @Test
  void emailInvalide() {
    assertThrows(IllegalArgumentException.class, () -> us.create("Alice", "invalide"));
    assertThrows(IllegalArgumentException.class, () -> us.create("Alice", "a b@c.test"));
  }

  @Test
  void nomObligatoire() {
    assertThrows(IllegalArgumentException.class, () -> us.create(" ", "a@b.test"));
    assertThrows(IllegalArgumentException.class, () -> ss.create("", 10));
  }

  @Test
  void creationSalle() {
    assertNotNull(s.getId());
    assertEquals(20, ss.find(s.getId()).orElseThrow().getCapacite());
  }

  @Test
  void capaciteInvalide() {
    assertThrows(IllegalArgumentException.class, () -> ss.create("Erreur", 0));
    assertThrows(IllegalArgumentException.class, () -> ss.create("Erreur", -1));
  }

  @Test
  void creationReservation() {
    Reservation r = reserve();
    assertNotNull(r.getId());
    assertEquals(0, r.getVersion());
  }

  @Test
  void relationUtilisateur() {
    reserve();
    assertEquals(
        Integer.valueOf(1),
        JpaUtil.<Integer>read(
            f, em -> em.find(Utilisateur.class, u.getId()).getReservations().size()));
  }

  @Test
  void relationSalle() {
    reserve();
    assertEquals(
        Integer.valueOf(1),
        JpaUtil.<Integer>read(f, em -> em.find(Salle.class, s.getId()).getReservations().size()));
  }

  @Test
  void associationsLazy() {
    Reservation r = reserve();
    JpaUtil.read(
        f,
        em -> {
          Reservation x = em.find(Reservation.class, r.getId());
          assertFalse(f.getPersistenceUnitUtil().isLoaded(x, "utilisateur"));
          x.toString();
          assertFalse(f.getPersistenceUnitUtil().isLoaded(x, "utilisateur"));
          return null;
        });
  }

  @Test
  void datesInvalides() {
    assertThrows(BusinessException.class, () -> rs.create(u.getId(), s.getId(), null, d));
    assertThrows(BusinessException.class, () -> rs.create(u.getId(), s.getId(), d, d));
    assertThrows(
        BusinessException.class, () -> rs.create(u.getId(), s.getId(), d, d.minusHours(1)));
  }

  @Test
  void datePassee() {
    assertThrows(
        BusinessException.class,
        () -> rs.create(u.getId(), s.getId(), d.minusYears(2), d.minusYears(2).plusHours(1)));
  }

  @Test
  void utilisateurAbsent() {
    assertThrows(
        BusinessException.class, () -> rs.create(Long.MAX_VALUE, s.getId(), d, d.plusHours(1)));
    assertTrue(rs.list().isEmpty());
  }

  @Test
  void salleAbsente() {
    assertTrue(ss.find(Long.MAX_VALUE).isEmpty());
    assertThrows(
        BusinessException.class, () -> rs.create(u.getId(), Long.MAX_VALUE, d, d.plusHours(1)));
  }

  @Test
  void modificationReservation() {
    Reservation r = reserve();
    r.setDateFin(d.plusHours(2));
    Reservation changed = rs.update(r);
    assertEquals(1, changed.getVersion());
    assertEquals(d.plusHours(2), rs.find(r.getId()).orElseThrow().getDateFin());
  }

  @Test
  void suppressionReservation() {
    Reservation r = reserve();
    rs.delete(r.getId());
    assertTrue(rs.find(r.getId()).isEmpty());
  }

  @Test
  void rechercheId() {
    Reservation r = reserve();
    assertEquals(r.getId(), rs.find(r.getId()).orElseThrow().getId());
    assertTrue(rs.find(Long.MAX_VALUE).isEmpty());
  }

  @Test
  void listeReservations() {
    reserve();
    assertEquals(1, rs.list().size());
  }

  @Test
  void crudUtilisateur() {
    u.setNom("Alice modifiée");
    assertEquals(1, us.update(u).getVersion());
    us.delete(u.getId());
    assertTrue(us.find(u.getId()).isEmpty());
  }

  @Test
  void crudSalle() {
    s.setDescription("Projecteur");
    assertEquals(1, ss.update(s).getVersion());
    assertEquals("Projecteur", ss.find(s.getId()).orElseThrow().getDescription());
    ss.delete(s.getId());
    assertTrue(ss.find(s.getId()).isEmpty());
  }

  @Test
  void suppressionReferencesRefusee() {
    reserve();
    assertThrows(BusinessException.class, () -> ss.delete(s.getId()));
    assertThrows(BusinessException.class, () -> us.delete(u.getId()));
    assertEquals(1, rs.list().size());
  }

  @Test
  void chevauchements() {
    reserve();
    for (int[] offset : new int[][] {{-30, 30}, {30, 90}, {-30, 90}, {15, 45}, {0, 60}})
      assertThrows(
          BusinessException.class,
          () ->
              rs.create(u.getId(), s.getId(), d.plusMinutes(offset[0]), d.plusMinutes(offset[1])));
    assertEquals(1, rs.list().size());
  }

  @Test
  void adjacentes() {
    reserve();
    rs.create(u.getId(), s.getId(), d.plusHours(1), d.plusHours(2));
    rs.create(u.getId(), s.getId(), d.minusHours(1), d);
    assertEquals(3, rs.list().size());
  }

  @Test
  void sallesDistinctes() {
    reserve();
    Salle other = ss.create("Salle B", 40);
    rs.create(u.getId(), other.getId(), d, d.plusHours(1));
    assertEquals(2, rs.list().size());
  }

  @Test
  void rollbackAtomique() {
    assertThrows(
        IllegalStateException.class,
        () ->
            JpaUtil.tx(
                f,
                em -> {
                  em.persist(new Salle("Rollback", 10));
                  throw new IllegalStateException("panne simulée avant commit");
                }));
    assertEquals(1, ss.list().size());
    assertNotNull(reserve().getId());
  }

  @Test
  void modificationEnConflitRollback() {
    Reservation a = reserve();
    Reservation b = rs.create(u.getId(), s.getId(), d.plusHours(2), d.plusHours(3));
    b.setDateDebut(d.plusMinutes(30));
    assertThrows(BusinessException.class, () -> rs.update(b));
    assertEquals(d.plusHours(2), rs.find(b.getId()).orElseThrow().getDateDebut());
    assertEquals(0, rs.find(a.getId()).orElseThrow().getVersion());
  }

  @Test
  void conflitOptimisteReel() {
    Reservation r = reserve();
    Demonstrations.optimistic(f, r.getId());
    assertEquals(1, rs.find(r.getId()).orElseThrow().getVersion());
    assertEquals(d.plusMinutes(65), rs.find(r.getId()).orElseThrow().getDateFin());
  }

  @Test
  void serviceRefuseVersionObsolete() {
    Reservation a = reserve(), b = rs.find(a.getId()).orElseThrow();
    a.setDateFin(d.plusMinutes(70));
    rs.update(a);
    b.setDateFin(d.plusMinutes(80));
    assertThrows(OptimisticLockException.class, () -> rs.update(b));
    assertEquals(d.plusMinutes(70), rs.find(a.getId()).orElseThrow().getDateFin());
  }

  @Test
  void versionUtilisateur() {
    Utilisateur other = us.find(u.getId()).orElseThrow();
    u.setNom("Premier");
    us.update(u);
    other.setNom("Obsolete");
    assertThrows(OptimisticLockException.class, () -> us.update(other));
  }

  @Test
  void versionSalle() {
    Salle other = ss.find(s.getId()).orElseThrow();
    s.setCapacite(30);
    ss.update(s);
    other.setCapacite(40);
    assertThrows(OptimisticLockException.class, () -> ss.update(other));
  }

  @Test
  void creationsSimultanees() throws Exception {
    assertEquals(1, Demonstrations.concurrent(f, u.getId(), s.getId(), d));
    assertEquals(1, rs.list().size());
  }

  @Test
  void verrouSalleBloqueCreation() throws Exception {
    EntityManager lock = f.createEntityManager();
    ExecutorService pool = Executors.newSingleThreadExecutor();
    CountDownLatch started = new CountDownLatch(1);
    try {
      lock.getTransaction().begin();
      lock.find(Salle.class, s.getId(), LockModeType.PESSIMISTIC_WRITE);
      Future<Reservation> pending =
          pool.submit(
              () -> {
                started.countDown();
                return reserve();
              });
      assertTrue(started.await(5, TimeUnit.SECONDS));
      assertThrows(TimeoutException.class, () -> pending.get(200, TimeUnit.MILLISECONDS));
      lock.getTransaction().commit();
      assertNotNull(pending.get(10, TimeUnit.SECONDS).getId());
    } finally {
      if (lock.getTransaction().isActive()) lock.getTransaction().rollback();
      lock.close();
      pool.shutdownNow();
      assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
    }
  }

  @Test
  void jpqlSalle() {
    Reservation r = reserve();
    assertEquals(List.of(r.getId()), ids(rs.byRoom(s.getId())));
  }

  @Test
  void jpqlUtilisateur() {
    Reservation r = reserve();
    assertEquals(List.of(r.getId()), ids(rs.byUser(u.getId())));
  }

  @Test
  void jpqlApresDate() {
    reserve();
    assertEquals(1, rs.after(d.minusSeconds(1)).size());
    assertTrue(rs.after(d).isEmpty());
  }

  @Test
  void intervalleDemiOuvert() {
    reserve();
    assertEquals(1, rs.between(d.plusMinutes(30), d.plusHours(2)).size());
    assertTrue(rs.between(d.plusHours(1), d.plusHours(2)).isEmpty());
    assertTrue(rs.between(d.minusHours(1), d).isEmpty());
  }

  @Test
  void criteriaUnFiltre() {
    reserve();
    assertEquals(1, rs.search("salle a", null, null, null, null).size());
  }

  @Test
  void criteriaPlusieursFiltres() {
    Reservation r = reserve();
    assertEquals(List.of(r.getId()), ids(rs.search("Salle A", d, d.plusHours(1), 20, u.getId())));
  }

  @Test
  void criteriaAucunFiltre() {
    reserve();
    assertEquals(ids(rs.list()), ids(rs.search(null, null, null, null, null)));
  }

  @Test
  void criteriaVide() {
    reserve();
    assertTrue(rs.search("absente", null, null, null, null).isEmpty());
    assertTrue(rs.search(null, null, null, 21, null).isEmpty());
    assertTrue(rs.search(null, null, null, null, Long.MAX_VALUE).isEmpty());
  }

  @Test
  void criteriaBornesEtEquivalence() {
    reserve();
    assertEquals(
        ids(rs.between(d, d.plusHours(1))), ids(rs.search(null, d, d.plusHours(1), null, null)));
    assertTrue(rs.search(null, d.plusHours(1), null, null, null).isEmpty());
    assertTrue(rs.search(null, null, d, null, null).isEmpty());
    assertThrows(BusinessException.class, () -> rs.search(null, d, d, null, null));
  }

  @Test
  void criteriaEchappeJokers() {
    reserve();
    assertTrue(rs.search("%", null, null, null, null).isEmpty());
  }

  @Test
  void disponibilites() {
    reserve();
    assertTrue(ss.available(d, d.plusHours(1)).isEmpty());
    assertEquals(1, ss.available(d.plusHours(1), d.plusHours(2)).size());
  }

  @Test
  void cacheL2EntreEntityManagers() {
    var x = Demonstrations.sample(f, s.getId());
    assertEquals(1, x.hits());
    assertEquals(1, x.misses());
    assertEquals(1, x.puts());
    assertEquals(1, x.sql());
  }

  @Test
  void comparaisonSansCache() {
    var on = Demonstrations.sample(f, s.getId());
    EntityManagerFactory off = JpaUtil.open(url, false, false);
    try {
      var x = Demonstrations.sample(off, s.getId());
      assertEquals(0, x.hits());
      assertEquals(2, x.sql());
      assertTrue(on.sql() < x.sql());
    } finally {
      off.close();
    }
  }

  @Test
  void queryCacheExplicite() {
    var stats = f.unwrap(SessionFactory.class).getStatistics();
    stats.clear();
    ss.cachedList();
    ss.cachedList();
    assertEquals(1, stats.getQueryCacheHitCount());
    assertEquals(1, stats.getQueryCacheMissCount());
  }

  @Test
  void jpqlSansHintNonCache() {
    var stats = f.unwrap(SessionFactory.class).getStatistics();
    stats.clear();
    ss.list();
    ss.list();
    assertEquals(0, stats.getQueryCacheHitCount());
    assertEquals(2, stats.getQueryExecutionCount());
  }

  @Test
  void invalidationCache() {
    Demonstrations.sample(f, s.getId());
    s.setCapacite(50);
    ss.update(s);
    assertEquals(50, ss.find(s.getId()).orElseThrow().getCapacite());
  }

  @Test
  void statistiquesReelles() {
    var stats = f.unwrap(SessionFactory.class).getStatistics();
    stats.clear();
    reserve();
    assertEquals(1, stats.getEntityInsertCount());
    assertEquals(1, stats.getSuccessfulTransactionCount());
    assertTrue(stats.getPrepareStatementCount() > 0);
  }

  @Test
  void rapportGenere() throws Exception {
    reserve();
    Path report = new PerformanceReport(f, temp).generateReport();
    assertTrue(Files.readString(report).contains("Prepared statements"));
    assertTrue(Files.size(temp.resolve("statistiques-hibernate.csv")) > 0);
  }

  @Test
  void initialisationReproductible() {
    rs.list(); // test sur une autre base entièrement vide
    String otherUrl = "jdbc:h2:mem:seed" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
    new MigrationExecutor(otherUrl).migrate();
    // Fermer la factory principale avant une nouvelle factory cachée (même URI JCache).
    f.close();
    EntityManagerFactory other = JpaUtil.open(otherUrl, true, false);
    try {
      DataInitializer.initialize(other);
      String counts = DataInitializer.counts(other);
      DataInitializer.initialize(other);
      assertEquals(counts, DataInitializer.counts(other));
      assertEquals("Utilisateurs=10, Salles=8, Reservations=32", counts);
      assertEquals(
          Long.valueOf(0),
          JpaUtil.<Long>read(
              other,
              em ->
                  em.createQuery(
                          "select count(a) from Reservation a, Reservation b where a.id<b.id and"
                              + " a.salle=b.salle and a.dateDebut<b.dateFin and"
                              + " a.dateFin>b.dateDebut",
                          Long.class)
                      .getSingleResult()));
    } finally {
      other.close();
    }
  }
}
