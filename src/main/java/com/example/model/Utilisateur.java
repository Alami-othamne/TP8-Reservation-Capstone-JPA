package com.example.model;

import java.util.*;
import javax.persistence.*;

@Entity
@Table(
    name = "utilisateurs",
    uniqueConstraints = @UniqueConstraint(name = "ux_utilisateurs_email", columnNames = "email"))
public class Utilisateur {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 120)
  private String nom;

  @Column(nullable = false, length = 254)
  private String email;

  @Version private int version;

  @OneToMany(mappedBy = "utilisateur")
  private List<Reservation> reservations = new ArrayList<>();

  public Utilisateur() {}

  public Utilisateur(String nom, String email) {
    setNom(nom);
    setEmail(email);
  }

  public Long getId() {
    return id;
  }

  public int getVersion() {
    return version;
  }

  public String getNom() {
    return nom;
  }

  public void setNom(String v) {
    nom = v;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String v) {
    email = v == null ? null : v.trim().toLowerCase(Locale.ROOT);
  }

  public List<Reservation> getReservations() {
    return Collections.unmodifiableList(reservations);
  }

  @PrePersist
  @PreUpdate
  public void validate() {
    if (nom == null || nom.isBlank() || nom.length() > 120)
      throw new IllegalArgumentException("Nom obligatoire (120 caractères maximum)");
    if (email == null || email.length() > 254 || !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))
      throw new IllegalArgumentException("Email invalide");
  }

  @Override
  public String toString() {
    return "Utilisateur[id="
        + id
        + ", nom="
        + nom
        + ", email="
        + email
        + ", version="
        + version
        + "]";
  }
}
