package com.example;

import static org.junit.jupiter.api.Assertions.*;

import com.example.util.*;
import java.nio.file.*;
import java.sql.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class MigrationTest {
  @TempDir Path temp;
  MigrationExecutor m;

  @BeforeEach
  void setup() {
    m = new MigrationExecutor("jdbc:h2:" + temp.resolve("migration").toString().replace('\\', '/'));
  }

  void v1() {
    m.apply(1, "db/migration/v1_0_initial_schema.sql");
  }

  void seed() throws Exception {
    try (var c = m.connect();
        var s = c.createStatement()) {
      s.execute(
          "INSERT INTO utilisateurs(nom,email,version) VALUES ('Temoin','temoin@example.test',0)");
      s.execute("INSERT INTO salles(nom,capacite,version) VALUES ('Salle',20,0)");
      s.execute(
          "INSERT INTO reservations(utilisateur_id,salle_id,date_debut,date_fin,version) SELECT"
              + " u.id,s.id,TIMESTAMP '2090-01-01 09:00:00',TIMESTAMP '2090-01-01 10:00:00',0 FROM"
              + " utilisateurs u CROSS JOIN salles s");
    }
  }

  @Test
  void schemaV1SansDescription() throws Exception {
    v1();
    try (var c = m.connect()) {
      assertFalse(MigrationExecutor.descriptionExists(c));
      assertFalse(MigrationExecutor.uniqueEmail(c));
    }
  }

  @Test
  void migrationV2EtValidation() throws Exception {
    v1();
    seed();
    m.migrate();
    try (var c = m.connect()) {
      assertTrue(MigrationExecutor.descriptionExists(c));
      assertTrue(MigrationExecutor.uniqueEmail(c));
    }
    var f =
        JpaUtil.open(
            "jdbc:h2:" + temp.resolve("migration").toString().replace('\\', '/'), false, false);
    try {
      assertEquals("Utilisateurs=1, Salles=1, Reservations=1", DataInitializer.counts(f));
    } finally {
      f.close();
    }
  }

  @Test
  void donneesConserveesApresReouverture() throws Exception {
    v1();
    seed();
    m.migrate();
    try (var c = m.connect();
        var st = c.createStatement();
        var r =
            st.executeQuery(
                "SELECT u.nom,u.email,s.capacite,r.version,r.date_debut FROM reservations r JOIN"
                    + " salles s ON r.salle_id=s.id JOIN utilisateurs u ON"
                    + " r.utilisateur_id=u.id")) {
      assertTrue(r.next());
      assertEquals("Temoin", r.getString(1));
      assertEquals("temoin@example.test", r.getString(2));
      assertEquals(20, r.getInt(3));
      assertEquals(0, r.getInt(4));
      assertEquals("2090-01-01 09:00:00.0", r.getTimestamp(5).toString());
    }
  }

  @Test
  void deuxiemePassageIdempotent() throws Exception {
    m.migrate();
    seed();
    m.migrate();
    try (var c = m.connect();
        var s = c.createStatement();
        var r = s.executeQuery("SELECT COUNT(*) FROM schema_migrations")) {
      r.next();
      assertEquals(2, r.getInt(1));
    }
  }

  @Test
  void scriptAbsent() {
    assertThrows(IllegalArgumentException.class, () -> m.apply(9, "absent.sql"));
  }

  @Test
  void v2ExigeV1() {
    assertThrows(
        IllegalStateException.class, () -> m.apply(2, "db/migration/v2_0_add_description.sql"));
  }

  @Test
  void contrainteSqlDatesEtCapacite() throws Exception {
    v1();
    seed();
    try (var c = m.connect();
        var s = c.createStatement()) {
      assertThrows(SQLException.class, () -> s.executeUpdate("UPDATE salles SET capacite=0"));
      assertThrows(
          SQLException.class, () -> s.executeUpdate("UPDATE reservations SET date_fin=date_debut"));
      assertThrows(SQLException.class, () -> s.executeUpdate("DELETE FROM utilisateurs"));
    }
  }

  @Test
  void uniqueSqlApresV2() throws Exception {
    v1();
    seed();
    m.migrate();
    try (var c = m.connect();
        var s = c.createStatement()) {
      assertThrows(
          SQLException.class,
          () ->
              s.executeUpdate(
                  "INSERT INTO utilisateurs(nom,email,version) VALUES"
                      + " ('Double','temoin@example.test',0)"));
    }
  }

  @Test
  void indexEquivalentNonRedondant() throws Exception {
    v1();
    try (var c = m.connect();
        var s = c.createStatement()) {
      s.execute("ALTER TABLE utilisateurs ADD CONSTRAINT autre_nom UNIQUE(email)");
    }
    m.migrate();
    try (var c = m.connect();
        var r = c.getMetaData().getIndexInfo(null, "PUBLIC", "UTILISATEURS", true, false)) {
      int email = 0;
      while (r.next()) if ("EMAIL".equals(r.getString("COLUMN_NAME"))) email++;
      assertEquals(1, email);
    }
  }

  @Test
  void repriseApresEchecDdlPartiel() throws Exception {
    v1();
    seed();
    try (var c = m.connect();
        var s = c.createStatement()) {
      s.execute(
          "INSERT INTO utilisateurs(nom,email,version) VALUES ('Double','temoin@example.test',0)");
    }
    assertThrows(IllegalStateException.class, () -> m.migrate());
    try (var c = m.connect();
        var s = c.createStatement()) {
      assertTrue(MigrationExecutor.descriptionExists(c));
      try (var r = s.executeQuery("SELECT COUNT(*) FROM schema_migrations WHERE version=2")) {
        r.next();
        assertEquals(0, r.getInt(1));
      }
      s.execute("DELETE FROM utilisateurs WHERE nom='Double'");
    }
    m.migrate();
    try (var c = m.connect()) {
      assertTrue(MigrationExecutor.uniqueEmail(c));
    }
  }
}
