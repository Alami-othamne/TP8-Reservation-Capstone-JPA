package com.example.util;

import com.example.service.BusinessException;
import java.util.*;
import java.util.function.Function;
import javax.persistence.*;

public final class JpaUtil {
  public static final String DEFAULT_URL = "jdbc:h2:./data/reservations;LOCK_TIMEOUT=10000";

  private JpaUtil() {}

  public static EntityManagerFactory open(String url, boolean cache, boolean sql) {
    Map<String, Object> p = new HashMap<>();
    p.put("javax.persistence.jdbc.url", url);
    p.put("hibernate.cache.use_second_level_cache", cache);
    p.put("hibernate.cache.use_query_cache", cache);
    p.put("hibernate.show_sql", sql);
    return Persistence.createEntityManagerFactory("ReservationPU", p);
  }

  public static <T> T read(EntityManagerFactory f, Function<EntityManager, T> action) {
    EntityManager em = f.createEntityManager();
    try {
      return action.apply(em);
    } finally {
      em.close();
    }
  }

  public static <T> T tx(EntityManagerFactory f, Function<EntityManager, T> action) {
    EntityManager em = f.createEntityManager();
    EntityTransaction tx = em.getTransaction();
    try {
      tx.begin();
      T result = action.apply(em);
      tx.commit();
      return result;
    } catch (RuntimeException ex) {
      if (tx.isActive()) tx.rollback();
      for (Throwable c = ex; c != null; c = c.getCause()) {
        if (c instanceof OptimisticLockException) throw (OptimisticLockException) c;
        if (c instanceof org.hibernate.exception.ConstraintViolationException)
          throw new BusinessException("Contrainte SQL violée (unicité, référence ou valeur)", ex);
      }
      throw ex;
    } finally {
      em.close();
    }
  }

  public static <T> T require(EntityManager em, Class<T> type, Long id) {
    if (id == null) throw new BusinessException("Identifiant requis");
    T value = em.find(type, id);
    if (value == null) throw new BusinessException(type.getSimpleName() + " introuvable : " + id);
    return value;
  }
}
