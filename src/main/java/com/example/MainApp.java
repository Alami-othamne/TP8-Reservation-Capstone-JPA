package com.example;

import com.example.model.*;
import com.example.service.*;
import com.example.util.*;
import java.nio.file.*;
import java.time.*;
import javax.persistence.*;

public class MainApp {
  private static void heading(String text) {
    System.out.println("\n=== " + text + " ===");
  }

  public static void main(String[] args) throws Exception {
    System.setProperty("org.jboss.logging.provider", "slf4j");
    System.out.println(
        "========================================\n"
            + " TP CAPSTONE - RESERVATION DE SALLES\n"
            + "========================================");
    Files.createDirectories(Path.of("data"));
    String url = JpaUtil.DEFAULT_URL;
    heading("VERIFICATION DES MIGRATIONS");
    new MigrationExecutor(url).migrate();
    heading("INITIALISATION HIBERNATE");
    EntityManagerFactory f = JpaUtil.open(url, true, true);
    try {
      PerformanceReport report = new PerformanceReport(f, Path.of("rapports"));
      UtilisateurService users = new UtilisateurService(f);
      SalleService rooms = new SalleService(f);
      ReservationService reservations = new ReservationService(f);
      heading("INITIALISATION DES DONNEES");
      DataInitializer.initialize(f);
      heading("LISTE DES UTILISATEURS");
      users.list().forEach(System.out::println);
      heading("LISTE DES SALLES");
      rooms.list().forEach(System.out::println);
      heading("LISTE DES RESERVATIONS");
      reservations.list().forEach(System.out::println);
      Long uid = users.list().get(0).getId(), sid = rooms.list().get(0).getId();
      LocalDateTime d = LocalDate.now(ZoneId.of("Africa/Casablanca")).plusDays(90).atTime(14, 0);
      heading("CREATION DE RESERVATION");
      Reservation demo = reservations.create(uid, sid, d, d.plusHours(1));
      System.out.println("Créée : " + demo);
      heading("MODIFICATION DE RESERVATION");
      demo.setDateFin(d.plusHours(2));
      demo = reservations.update(demo);
      System.out.println("Modifiée : " + demo);
      heading("REQUETES JPQL");
      System.out.println(
          "Par salle="
              + reservations.byRoom(sid).size()
              + ", par utilisateur="
              + reservations.byUser(uid).size()
              + ", après date="
              + reservations.after(d.minusDays(1)).size()
              + ", intersection intervalle="
              + reservations.between(d, d.plusHours(3)).size());
      heading("CRITERIA API");
      System.out.println(
          "Aucun filtre=" + reservations.search(null, null, null, null, null).size());
      System.out.println(
          "Nom salle=" + reservations.search("Salle A", null, null, null, null).size());
      System.out.println(
          "Filtres combinés=" + reservations.search("Salle A", d, d.plusHours(3), 20, uid).size());
      System.out.println(
          "Aucun résultat=" + reservations.search("inexistante", null, null, null, null).size());
      heading("VERIFICATION DES DISPONIBILITES");
      System.out.println("Salles libres=" + rooms.available(d, d.plusHours(1)).size());
      try {
        reservations.create(uid, sid, d, d.plusMinutes(30));
        throw new IllegalStateException("Conflit non détecté");
      } catch (BusinessException ex) {
        System.out.println("Refus attendu : " + ex.getMessage());
      }
      heading("TEST VERROUILLAGE OPTIMISTE");
      Demonstrations.optimistic(f, demo.getId());
      heading("SUPPRESSION DE RESERVATION");
      reservations.delete(demo.getId());
      System.out.println(
          "Suppression vérifiée : présent=" + reservations.find(demo.getId()).isPresent());
      heading("TEST CREATIONS CONCURRENTES");
      Demonstrations.concurrent(f, uid, sid, d.plusDays(1));
      for (Reservation r : reservations.between(d.plusDays(1), d.plusDays(1).plusHours(1)))
        reservations.delete(r.getId());
      heading("TEST CACHE SECOND NIVEAU");
      Demonstrations.cache(f, url, sid);
      heading("STATISTIQUES HIBERNATE");
      report.metrics().forEach((k, v) -> System.out.println(k + "=" + v));
      heading("RAPPORT DE PERFORMANCE");
      System.out.println("Rapport enregistré : " + report.generateReport());
      System.out.println("État final : " + DataInitializer.counts(f));
    } finally {
      f.close();
    }
    heading("FIN APPLICATION");
  }
}
