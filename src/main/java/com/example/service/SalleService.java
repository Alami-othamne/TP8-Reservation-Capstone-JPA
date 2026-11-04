package com.example.service;

import com.example.model.*;
import com.example.util.JpaUtil;
import java.time.LocalDateTime;
import java.util.*;
import javax.persistence.*;

public class SalleService {
  private final EntityManagerFactory f;

  public SalleService(EntityManagerFactory f) {
    this.f = f;
  }

  public Salle create(String nom, int capacite) {
    return JpaUtil.tx(
        f,
        em -> {
          Salle s = new Salle(nom, capacite);
          s.validate();
          em.persist(s);
          return s;
        });
  }

  public Salle update(Salle s) {
    s.validate();
    return JpaUtil.tx(
        f,
        em -> {
          JpaUtil.require(em, Salle.class, s.getId());
          return em.merge(s);
        });
  }

  public Optional<Salle> find(Long id) {
    return JpaUtil.read(f, em -> Optional.ofNullable(em.find(Salle.class, id)));
  }

  public List<Salle> list() {
    return JpaUtil.read(
        f,
        em -> em.createQuery("select s from Salle s order by s.id", Salle.class).getResultList());
  }

  public void delete(Long id) {
    JpaUtil.tx(
        f,
        em -> {
          Salle s = JpaUtil.require(em, Salle.class, id);
          em.lock(s, LockModeType.PESSIMISTIC_WRITE);
          if (em.createQuery("select count(r) from Reservation r where r.salle.id=:id", Long.class)
                  .setParameter("id", id)
                  .getSingleResult()
              > 0) throw new BusinessException("Salle référencée par des réservations");
          em.remove(s);
          return null;
        });
  }

  public List<Salle> available(LocalDateTime debut, LocalDateTime fin) {
    ReservationService.interval(debut, fin);
    return JpaUtil.read(
        f,
        em ->
            em.createQuery(
                    "select s from Salle s where not exists (select r.id from Reservation r where"
                        + " r.salle=s and r.dateDebut<:fin and r.dateFin>:debut) order by s.id",
                    Salle.class)
                .setParameter("debut", debut)
                .setParameter("fin", fin)
                .getResultList());
  }

  public List<Salle> cachedList() {
    return JpaUtil.read(
        f,
        em ->
            em.createQuery("select s from Salle s order by s.id", Salle.class)
                .setHint("org.hibernate.cacheable", true)
                .getResultList());
  }
}
