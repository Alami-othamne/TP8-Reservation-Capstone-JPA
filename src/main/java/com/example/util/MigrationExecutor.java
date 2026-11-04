package com.example.util;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import org.h2.tools.RunScript;

public class MigrationExecutor {
  private final String url;

  public MigrationExecutor(String url) {
    this.url = url;
  }

  public Connection connect() throws SQLException {
    return DriverManager.getConnection(url, "sa", "");
  }

  public void migrate() {
    apply(1, "db/migration/v1_0_initial_schema.sql");
    apply(2, "db/migration/v2_0_add_description.sql");
  }

  public void apply(int version, String resource) {
    try (InputStream in = MigrationExecutor.class.getClassLoader().getResourceAsStream(resource)) {
      if (in == null)
        throw new IllegalArgumentException("Script SQL absent du classpath : " + resource);
      String script = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      try (Connection c = connect();
          Statement s = c.createStatement()) {
        s.execute(
            "CREATE TABLE IF NOT EXISTS schema_migrations(version INT PRIMARY KEY, script"
                + " VARCHAR(255) NOT NULL, applied_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT"
                + " NULL)");
        try (PreparedStatement p =
            c.prepareStatement("SELECT script FROM schema_migrations WHERE version=?")) {
          p.setInt(1, version);
          try (ResultSet r = p.executeQuery()) {
            if (r.next()) {
              if (!resource.equals(r.getString(1)))
                throw new IllegalStateException("Version déjà liée à un autre script");
              return;
            }
          }
        }
        if (version == 2 && !applied(c, 1))
          throw new IllegalStateException("Appliquer v1 avant v2");
        if (version == 2 && uniqueEmail(c))
          script =
              script.replace(
                  "CREATE UNIQUE INDEX IF NOT EXISTS ux_utilisateurs_email ON utilisateurs(email);",
                  "");
        // RunScript analyse les instructions, commentaires et chaînes SQL séparément.
        // H2 valide implicitement le DDL : reprise par scripts idempotents, pas de faux rollback
        // global.
        RunScript.execute(c, new StringReader(script));
        try (PreparedStatement p =
            c.prepareStatement("INSERT INTO schema_migrations(version,script) VALUES (?,?)")) {
          p.setInt(1, version);
          p.setString(2, resource);
          p.executeUpdate();
        }
        System.out.println("Migration v" + version + " appliquée : " + resource);
      }
    } catch (IOException | SQLException ex) {
      throw new IllegalStateException(
          "Échec migration v"
              + version
              + " ("
              + resource
              + "). DDL possiblement partiel ; corriger la cause puis relancer le script"
              + " idempotent. "
              + ex.getMessage(),
          ex);
    }
  }

  private boolean applied(Connection c, int version) throws SQLException {
    try (PreparedStatement p =
        c.prepareStatement("SELECT COUNT(*) FROM schema_migrations WHERE version=?")) {
      p.setInt(1, version);
      try (ResultSet r = p.executeQuery()) {
        r.next();
        return r.getInt(1) == 1;
      }
    }
  }

  public static boolean descriptionExists(Connection c) throws SQLException {
    try (ResultSet r = c.getMetaData().getColumns(null, "PUBLIC", "SALLES", "DESCRIPTION")) {
      return r.next();
    }
  }

  public static boolean uniqueEmail(Connection c) throws SQLException {
    Map<String, List<String>> indexes = new HashMap<>();
    try (ResultSet r = c.getMetaData().getIndexInfo(null, "PUBLIC", "UTILISATEURS", true, false)) {
      while (r.next()) {
        String name = r.getString("INDEX_NAME"), col = r.getString("COLUMN_NAME");
        if (name != null && col != null)
          indexes.computeIfAbsent(name, k -> new ArrayList<>()).add(col);
      }
    }
    return indexes.values().stream()
        .anyMatch(cols -> cols.size() == 1 && "EMAIL".equalsIgnoreCase(cols.get(0)));
  }
}
