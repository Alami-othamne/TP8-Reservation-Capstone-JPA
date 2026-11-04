package com.example.service;

import com.example.model.*;
import com.example.util.JpaUtil;
import java.util.*;
import javax.persistence.*;

public class UtilisateurService {
  private final EntityManagerFactory f;

  public UtilisateurService(EntityManagerFactory f) {
    this.f = f;
  }

  public Utilisateur create(String nom, String email) {
    return JpaUtil.tx(
        f,
        em -> {
          Utilisateur u = new Utilisateur(nom, email);
          u.validate();
          em.persist(u);
          return u;
        });
  }

  public Utilisateur update(Utilisateur u) {
    u.validate();
    return JpaUtil.tx(
        f,
        em -> {
          JpaUtil.require(em, Utilisateur.class, u.getId());
          return em.merge(u);
        });
  }

  public Optional<Utilisateur> find(Long id) {
    return JpaUtil.read(f, em -> Optional.ofNullable(em.find(Utilisateur.class, id)));
  }

  public List<Utilisateur> list() {
    return JpaUtil.read(
        f,
        em ->
            em.createQuery("select u from Utilisateur u order by u.id", Utilisateur.class)
                .getResultList());
  }

  public void delete(Long id) {
    JpaUtil.tx(
        f,
        em -> {
          Utilisateur u = JpaUtil.require(em, Utilisateur.class, id);
          if (em.createQuery(
                      "select count(r) from Reservation r where r.utilisateur.id=:id", Long.class)
                  .setParameter("id", id)
                  .getSingleResult()
              > 0) throw new BusinessException("Utilisateur référencé par des réservations");
          em.remove(u);
          return null;
        });
  }
}
