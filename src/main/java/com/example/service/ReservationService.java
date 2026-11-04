package com.example.service;

import com.example.model.*;
import com.example.util.JpaUtil;
import java.time.*;
import java.util.*;
import javax.persistence.*;
import javax.persistence.criteria.*;

public class ReservationService {
  private final EntityManagerFactory f;
  private final Clock clock;

  public ReservationService(EntityManagerFactory f) {
    this(f, Clock.system(ZoneId.of("Africa/Casablanca")));
  }

  public ReservationService(EntityManagerFactory f, Clock clock) {
    this.f = f;
    this.clock = clock;
  }

  public static void interval(LocalDateTime d, LocalDateTime e) {
    if (d == null || e == null || !e.isAfter(d))
      throw new BusinessException("Intervalle invalide : fin strictement après début");
  }

  private void future(LocalDateTime d, LocalDateTime e) {
    interval(d, e);
    if (d.isBefore(LocalDateTime.now(clock))) throw new BusinessException("Début dans le passé");
  }

  private Salle lockRoom(EntityManager em, Long id) {
    if (id == null) throw new BusinessException("Salle requise");
    Salle s = em.find(Salle.class, id, LockModeType.PESSIMISTIC_WRITE);
    if (s == null) throw new BusinessException("Salle introuvable : " + id);
    return s;
  }

  private void check(EntityManager em, Long room, LocalDateTime d, LocalDateTime e, Long excluded) {
    String q =
        "select count(r) from Reservation r where r.salle.id=:s and r.dateDebut<:e and r.dateFin>:d"
            + (excluded == null ? "" : " and r.id<>:id");
    TypedQuery<Long> query =
        em.createQuery(q, Long.class)
            .setParameter("s", room)
            .setParameter("e", e)
            .setParameter("d", d);
    if (excluded != null) query.setParameter("id", excluded);
    if (query.getSingleResult() > 0)
      throw new BusinessException("Chevauchement horaire pour la salle " + room);
  }

  public Reservation create(Long user, Long room, LocalDateTime d, LocalDateTime e) {
    future(d, e);
    return JpaUtil.tx(
        f,
        em -> {
          Salle s = lockRoom(em, room);
          Utilisateur u = JpaUtil.require(em, Utilisateur.class, user);
          check(em, room, d, e, null);
          Reservation r = new Reservation(u, s, d, e);
          em.persist(r);
          return r;
        });
  }

  /** Le DTO détaché conserve @Version ; merge vérifie réellement sa fraîcheur. */
  public Reservation update(Reservation detached) {
    detached.validate();
    future(detached.getDateDebut(), detached.getDateFin());
    return JpaUtil.tx(
        f,
        em -> {
          Reservation current = JpaUtil.require(em, Reservation.class, detached.getId());
          Long oldRoom = current.getSalle().getId(), newRoom = detached.getSalle().getId();
          if (newRoom == null) throw new BusinessException("Salle persistée requise");
          // Ordre stable pour éviter les interblocages lors d'échanges de salles.
          for (Long id : new TreeSet<>(Arrays.asList(oldRoom, newRoom))) lockRoom(em, id);
          JpaUtil.require(em, Utilisateur.class, detached.getUtilisateur().getId());
          Reservation merged =
              em.merge(detached); // ne pas copier les champs dans current : perte de version
          check(em, newRoom, merged.getDateDebut(), merged.getDateFin(), merged.getId());
          em.flush();
          return merged;
        });
  }

  public void delete(Long id) {
    JpaUtil.tx(
        f,
        em -> {
          Reservation r = JpaUtil.require(em, Reservation.class, id);
          lockRoom(em, r.getSalle().getId());
          em.remove(r);
          return null;
        });
  }

  private static final String FETCH =
      "select r from Reservation r join fetch r.salle join fetch r.utilisateur";

  public Optional<Reservation> find(Long id) {
    return JpaUtil.read(
        f,
        em ->
            em.createQuery(FETCH + " where r.id=:id", Reservation.class)
                .setParameter("id", id)
                .getResultStream()
                .findFirst());
  }

  public List<Reservation> list() {
    return JpaUtil.read(
        f,
        em ->
            em.createQuery(FETCH + " order by r.dateDebut,r.id", Reservation.class)
                .getResultList());
  }

  public List<Reservation> byRoom(Long id) {
    return byId("salle", id);
  }

  public List<Reservation> byUser(Long id) {
    return byId("utilisateur", id);
  }

  private List<Reservation> byId(String association, Long id) {
    return JpaUtil.read(
        f,
        em ->
            em.createQuery(
                    FETCH + " where r." + association + ".id=:id order by r.dateDebut,r.id",
                    Reservation.class)
                .setParameter("id", id)
                .getResultList());
  }

  public List<Reservation> after(LocalDateTime date) {
    Objects.requireNonNull(date);
    return JpaUtil.read(
        f,
        em ->
            em.createQuery(
                    FETCH + " where r.dateDebut>:date order by r.dateDebut,r.id", Reservation.class)
                .setParameter("date", date)
                .getResultList());
  }

  /** Intersection d'intervalles semi-ouverts [début, fin). */
  public List<Reservation> between(LocalDateTime d, LocalDateTime e) {
    interval(d, e);
    return JpaUtil.read(
        f,
        em ->
            em.createQuery(
                    FETCH + " where r.dateDebut<:e and r.dateFin>:d order by r.dateDebut,r.id",
                    Reservation.class)
                .setParameter("d", d)
                .setParameter("e", e)
                .getResultList());
  }

  public List<Reservation> search(
      String name, LocalDateTime from, LocalDateTime to, Integer capacity, Long user) {
    if (from != null && to != null) interval(from, to);
    if (capacity != null && capacity <= 0)
      throw new BusinessException("Capacité minimale positive requise");
    return JpaUtil.read(
        f,
        em -> {
          CriteriaBuilder cb = em.getCriteriaBuilder();
          CriteriaQuery<Reservation> q = cb.createQuery(Reservation.class);
          Root<Reservation> r = q.from(Reservation.class);
          r.fetch("salle");
          r.fetch("utilisateur");
          Join<Reservation, Salle> s = r.join("salle");
          List<Predicate> p = new ArrayList<>();
          if (name != null && !name.isBlank())
            p.add(
                cb.like(
                    cb.lower(s.get("nom")),
                    "%"
                        + name.toLowerCase(Locale.ROOT)
                            .replace("!", "!!")
                            .replace("%", "!%")
                            .replace("_", "!_")
                        + "%",
                    '!'));
          if (from != null) p.add(cb.greaterThan(r.get("dateFin"), from));
          if (to != null) p.add(cb.lessThan(r.get("dateDebut"), to));
          if (capacity != null) p.add(cb.ge(s.get("capacite"), capacity));
          if (user != null) p.add(cb.equal(r.get("utilisateur").get("id"), user));
          q.select(r)
              .where(p.toArray(new Predicate[0]))
              .orderBy(cb.asc(r.get("dateDebut")), cb.asc(r.get("id")));
          return em.createQuery(q).getResultList();
        });
  }
}
