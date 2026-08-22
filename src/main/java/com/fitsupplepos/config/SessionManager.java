package com.fitsupplepos.config;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Every DAO and service in this app goes through here to open a Session and, when
 * writing, wrap it in a transaction with commit-on-success / rollback-on-failure.
 * Nothing outside this class deals with SessionFactory directly.
 */
public final class SessionManager {

    private SessionManager() {}

    private static SessionFactory factory() {
        return HibernateConfig.getSessionFactory();
    }

    /** Read-only unit of work. No transaction is opened; use for queries and simple gets. */
    public static <R> R withSession(Function<Session, R> work) {
        try (Session session = factory().openSession()) {
            return work.apply(session);
        }
    }

    /** Read-only unit of work with no return value. */
    public static void withSessionVoid(Consumer<Session> work) {
        try (Session session = factory().openSession()) {
            work.accept(session);
        }
    }

    /** Read-write unit of work: commits on success, rolls back and rethrows on any exception. */
    public static <R> R withTransaction(Function<Session, R> work) {
        try (Session session = factory().openSession()) {
            Transaction tx = session.beginTransaction();
            try {
                R result = work.apply(session);
                tx.commit();
                return result;
            } catch (RuntimeException e) {
                if (tx.isActive()) {
                    tx.rollback();
                }
                throw e;
            }
        }
    }

    /** Read-write unit of work with no return value. */
    public static void withTransactionVoid(Consumer<Session> work) {
        withTransaction(session -> {
            work.accept(session);
            return null;
        });
    }
}
