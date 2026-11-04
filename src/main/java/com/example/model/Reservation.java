package com.example.model;

import java.time.LocalDateTime;
import javax.persistence.*;

@Entity
@Table(
    name = "reservations",
    indexes = {
      @Index(name = "ix_reservation_salle_dates", columnList = "salle_id,date_debut,date_fin"),
      @Index(name = "ix_reservation_utilisateur", columnList = "utilisateur_id")
    })
public class Reservation {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "utilisateur_id", nullable = false)
  private Utilisateur utilisateur;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "salle_id", nullable = false)
  private Salle salle;

  @Column(name = "date_debut", nullable = false)
  private LocalDateTime dateDebut;

  @Column(name = "date_fin", nullable = false)
  private LocalDateTime dateFin;

  @Version private int version;

  public Reservation() {}

  public Reservation(Utilisateur u, Salle s, LocalDateTime d, LocalDateTime f) {
    utilisateur = u;
    salle = s;
    dateDebut = d;
    dateFin = f;
  }

  public Long getId() {
    return id;
  }

  public int getVersion() {
    return version;
  }

  public Utilisateur getUtilisateur() {
    return utilisateur;
  }

  public void setUtilisateur(Utilisateur v) {
    utilisateur = v;
  }

  public Salle getSalle() {
    return salle;
  }

  public void setSalle(Salle v) {
    salle = v;
  }

  public LocalDateTime getDateDebut() {
    return dateDebut;
  }

  public void setDateDebut(LocalDateTime v) {
    dateDebut = v;
  }

  public LocalDateTime getDateFin() {
    return dateFin;
  }

  public void setDateFin(LocalDateTime v) {
    dateFin = v;
  }

  @PrePersist
  @PreUpdate
  public void validate() {
    if (utilisateur == null || salle == null)
      throw new IllegalArgumentException("Utilisateur et salle requis");
    if (dateDebut == null || dateFin == null || !dateFin.isAfter(dateDebut))
      throw new IllegalArgumentException("Intervalle invalide");
  }

  @Override
  public String toString() {
    return "Reservation[id="
        + id
        + ", debut="
        + dateDebut
        + ", fin="
        + dateFin
        + ", version="
        + version
        + "]";
  }
}
