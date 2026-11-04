package com.example.util;

import java.nio.file.*;
import java.sql.*;
import javax.persistence.*;

/** Mode v1 puis v2 dans deux processus Maven distincts. */
public class MigrationDemo {
  public static final String URL = "jdbc:h2:./data/migration-proof;LOCK_TIMEOUT=10000";

  public static void main(String[] args) throws Exception {
    Files.createDirectories(Path.of("data"));
    MigrationExecutor m = new MigrationExecutor(URL);
    if (args.length != 1) throw new IllegalArgumentException("Mode attendu : v1 ou v2");
    if ("v1".equals(args[0])) {
      m.apply(1, "db/migration/v1_0_initial_schema.sql");
      try (Connection c = m.connect();
          Statement s = c.createStatement()) {
        if (MigrationExecutor.descriptionExists(c)) {
          System.out.println(
              "Base témoin déjà en v2 ; preuve initiale conservée dans le journal du premier"
                  + " passage.");
          return;
        }
        s.executeUpdate(
            "INSERT INTO utilisateurs(nom,email,version) SELECT 'Temoin"
                + " migration','temoin@example.test',0 WHERE NOT EXISTS (SELECT 1 FROM"
                + " utilisateurs)");
        s.executeUpdate(
            "INSERT INTO salles(nom,capacite,version) SELECT 'Salle temoin',20,0 WHERE NOT EXISTS"
                + " (SELECT 1 FROM salles)");
        s.executeUpdate(
            "INSERT INTO reservations(utilisateur_id,salle_id,date_debut,date_fin,version) SELECT"
                + " u.id,s.id,TIMESTAMP '2099-01-01 09:00:00',TIMESTAMP '2099-01-01 10:00:00',0"
                + " FROM utilisateurs u CROSS JOIN salles s WHERE NOT EXISTS (SELECT 1 FROM"
                + " reservations)");
        System.out.println(
            "PREUVE V1 : description présente="
                + MigrationExecutor.descriptionExists(c)
                + " ; "
                + counts(c));
      }
      System.out.println("Connexion v1 fermée ; base persistante sur disque.");
    } else if ("v2".equals(args[0])) {
      String before;
      try (Connection c = m.connect()) {
        before = counts(c);
        System.out.println(
            "Avant migration au redémarrage : description="
                + MigrationExecutor.descriptionExists(c)
                + " ; "
                + before);
      }
      m.migrate();
      m.migrate();
      try (Connection c = m.connect()) {
        if (!before.equals(counts(c))
            || !MigrationExecutor.descriptionExists(c)
            || !MigrationExecutor.uniqueEmail(c))
          throw new IllegalStateException("Migration non conforme");
        System.out.println(
            "PREUVE V2 : description=true ; index email unique=true ; "
                + counts(c)
                + " ; données conservées");
        try (ResultSet r =
            c.createStatement().executeQuery("SELECT nom, email FROM utilisateurs")) {
          r.next();
          if (!"temoin@example.test".equals(r.getString(2)))
            throw new IllegalStateException("Valeurs altérées");
          System.out.println("Témoin conservé : " + r.getString(1) + " / " + r.getString(2));
        }
      }
      EntityManagerFactory f = JpaUtil.open(URL, true, false);
      try {
        System.out.println(
            "JPA redémarrée : hbm2ddl=validate réussi ; " + DataInitializer.counts(f));
      } finally {
        f.close();
      }
      System.out.println("Deuxième passage : aucune migration rejouée.");
    } else throw new IllegalArgumentException("Mode inconnu");
  }

  private static String counts(Connection c) throws SQLException {
    StringBuilder out = new StringBuilder();
    for (String table : new String[] {"utilisateurs", "salles", "reservations"})
      try (Statement s = c.createStatement();
          ResultSet r = s.executeQuery("SELECT COUNT(*) FROM " + table)) {
        r.next();
        out.append(table).append('=').append(r.getInt(1)).append(' ');
      }
    return out.toString().trim();
  }
}
