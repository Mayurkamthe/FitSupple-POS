package com.fitsupplepos.dao;

import com.fitsupplepos.config.SessionManager;
import org.hibernate.Session;
import org.hibernate.query.Query;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Base DAO with common CRUD operations shared by every entity-specific DAO.
 * Entity-specific DAOs extend this and add their own finder queries.
 */
public abstract class GenericDao<T, ID> {

    private final Class<T> entityClass;

    protected GenericDao(Class<T> entityClass) {
        this.entityClass = entityClass;
    }

    public Optional<T> findById(ID id) {
        return SessionManager.withSession(session -> Optional.ofNullable(session.get(entityClass, id)));
    }

    public List<T> findAll() {
        return SessionManager.withSession(session ->
                session.createQuery("from " + entityClass.getSimpleName(), entityClass).list());
    }

    public T save(T entity) {
        return SessionManager.withTransaction(session -> {
            session.persist(entity);
            return entity;
        });
    }

    public T update(T entity) {
        return SessionManager.withTransaction(session -> session.merge(entity));
    }

    public void delete(T entity) {
        SessionManager.withTransactionVoid(session -> session.remove(session.contains(entity) ? entity : session.merge(entity)));
    }

    public void deleteById(ID id) {
        SessionManager.withTransactionVoid(session -> {
            T ref = session.get(entityClass, id);
            if (ref != null) {
                session.remove(ref);
            }
        });
    }

    public long count() {
        return SessionManager.withSession(session ->
                session.createQuery("select count(e) from " + entityClass.getSimpleName() + " e", Long.class)
                        .getSingleResult());
    }

    /** Escape hatch for DAO subclasses that need a custom read-only query within an open session. */
    protected <R> R query(Function<Session, R> fn) {
        return SessionManager.withSession(fn);
    }

    protected Class<T> getEntityClass() {
        return entityClass;
    }
}
