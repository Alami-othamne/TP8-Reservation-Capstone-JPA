package com.example.model;

import java.util.*;
import javax.persistence.*;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

@Entity
@Table(name = "salles")
@Cacheable
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class Salle {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 120)
  private String nom;

  @Column(nullable = false)
  private int capacite;

  @Column(length = 1000)
  private String description;

  @Version private int version;

  @OneToMany(mappedBy = "salle")
  private List<Reservation> reservations = new ArrayList<>();

  public Salle() {}

  public Salle(String nom, int capacite) {
    this.nom = nom;
    this.capacite = capacite;
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

  public int getCapacite() {
    return capacite;
  }

  public void setCapacite(int v) {
    capacite = v;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String v) {
    description = v;
  }

  public List<Reservation> getReservations() {
    return Collections.unmodifiableList(reservations);
  }

  @PrePersist
  @PreUpdate
  public void validate() {
    if (nom == null || nom.isBlank() || nom.length() > 120)
      throw new IllegalArgumentException("Nom obligatoire (120 caractères maximum)");
    if (capacite <= 0) throw new IllegalArgumentException("Capacité strictement positive requise");
    if (description != null && description.length() > 1000)
      throw new IllegalArgumentException("Description trop longue");
  }

  @Override
  public String toString() {
    return "Salle[id="
        + id
        + ", nom="
        + nom
        + ", capacite="
        + capacite
        + ", version="
        + version
        + "]";
  }
}
