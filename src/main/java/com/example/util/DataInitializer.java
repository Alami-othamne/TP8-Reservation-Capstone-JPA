package com.example.util;

import com.example.model.*;
import java.time.*;
import javax.persistence.*;

public final class DataInitializer {
  private DataInitializer() {}

  public static void initialize(EntityManagerFactory f) {
    JpaUtil.tx(
        f,
        em -> {
          long users =
              em.createQuery("select count(u) from Utilisateur u", Long.class).getSingleResult();
          if (users > 0) {
            System.out.println("Jeu existant conservé : aucune réinitialisation.");
            return null;
          }
          Utilisateur[] us = new Utilisateur[10];
          Salle[] ss = new Salle[8];
          String[] names = {
            "Salle A",
            "Salle B",
            "Salle C",
            "Salle Informatique",
            "Salle Réunion",
            "Amphithéâtre",
            "Salle Projet",
            "Salle Formation"
          };
          int[] sizes = {20, 40, 12, 25, 10, 120, 16, 30};
          for (int i = 0; i < us.length; i++) {
            us[i] = new Utilisateur("Etudiant " + (i + 1), "etudiant" + (i + 1) + "@example.test");
            em.persist(us[i]);
          }
          for (int i = 0; i < ss.length; i++) {
            ss[i] = new Salle(names[i], sizes[i]);
            ss[i].setDescription("Salle du campus : " + names[i]);
            em.persist(ss[i]);
          }
          LocalDateTime base =
              LocalDate.now(ZoneId.of("Africa/Casablanca")).plusDays(7).atTime(9, 0);
          for (int i = 0; i < 32; i++) {
            LocalDateTime d = base.plusDays(i / 8);
            em.persist(new Reservation(us[i % 10], ss[i % 8], d, d.plusHours(2)));
          }
          return null;
        });
    System.out.println(counts(f));
  }

  public static String counts(EntityManagerFactory f) {
    return JpaUtil.read(
        f,
        em ->
            "Utilisateurs="
                + em.createQuery("select count(u) from Utilisateur u", Long.class).getSingleResult()
                + ", Salles="
                + em.createQuery("select count(s) from Salle s", Long.class).getSingleResult()
                + ", Reservations="
                + em.createQuery("select count(r) from Reservation r", Long.class)
                    .getSingleResult());
  }
}
