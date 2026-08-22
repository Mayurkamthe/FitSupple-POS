package com.fitsupplepos.util;

import com.fitsupplepos.config.SessionManager;
import com.fitsupplepos.model.AuditLog;
import com.fitsupplepos.service.AuthService;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared helper for writing {@link AuditLog} entries. Every module that changes data
 * (sales, purchases, returns, settings) should call this instead of hand-rolling its
 * own persist(new AuditLog(...)) — keeps "who did what" consistent across the app.
 *
 * A failure to write an audit entry never blocks the underlying business operation:
 * it's logged and swallowed, the same policy AuthService already used for login/logout.
 */
public final class AuditLogger {

    private static final Logger log = LoggerFactory.getLogger(AuditLogger.class);

    private AuditLogger() {}

    private static String currentActor() {
        return AuthService.getCurrentUser().map(u -> u.getUsername()).orElse("system");
    }

    /** Writes an audit entry using its own transaction — use when not already inside one. */
    public static void log(String action, String entityName, String entityId, String details) {
        try {
            String who = currentActor();
            SessionManager.withTransactionVoid(session ->
                    session.persist(new AuditLog(action, entityName, entityId, details, who)));
        } catch (Exception e) {
            log.warn("Failed to write audit log for action {}", action, e);
        }
    }

    /** Writes an audit entry using an already-open Session/transaction (e.g. inside SaleService's transaction). */
    public static void log(Session session, String action, String entityName, String entityId, String details) {
        try {
            session.persist(new AuditLog(action, entityName, entityId, details, currentActor()));
        } catch (Exception e) {
            log.warn("Failed to write audit log for action {}", action, e);
        }
    }
}
