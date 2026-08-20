package com.fitsupplepos.service;

import com.fitsupplepos.config.SessionManager;
import com.fitsupplepos.dao.UserDao;
import com.fitsupplepos.exception.AuthenticationException;
import com.fitsupplepos.model.AuditLog;
import com.fitsupplepos.model.User;
import com.fitsupplepos.util.PasswordUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Handles owner authentication, password change, and the in-memory "current session".
 * There is only ever one owner account — no role/permission checks anywhere in the app.
 */
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final String DEFAULT_USERNAME = "owner";
    private static final String DEFAULT_PASSWORD = "owner@123";

    private final UserDao userDao = new UserDao();

    /** In-memory handle to whoever is currently logged in. Cleared on logout. */
    private static volatile User currentUser;

    /** Ensures a default OWNER account exists on first run. Idempotent. */
    public void ensureDefaultOwnerExists() {
        if (!userDao.anyUserExists()) {
            User owner = new User();
            owner.setUsername(DEFAULT_USERNAME);
            owner.setFullName("Owner");
            owner.setPasswordHash(PasswordUtil.hash(DEFAULT_PASSWORD));
            owner.setMustChangePassword(true);
            userDao.save(owner);
            log.info("Created default OWNER account (username='{}'). Default password must be changed on first login.", DEFAULT_USERNAME);
        }
    }

    public User login(String username, String password) {
        Optional<User> found = userDao.findByUsername(username);
        User user = found.orElseThrow(() -> new AuthenticationException("Invalid username or password."));

        if (!PasswordUtil.matches(password, user.getPasswordHash())) {
            throw new AuthenticationException("Invalid username or password.");
        }

        user.setLastLoginAt(LocalDateTime.now());
        userDao.update(user);
        writeAudit("LOGIN", user.getUsername(), "Owner logged in");

        currentUser = user;
        return user;
    }

    public void logout() {
        if (currentUser != null) {
            writeAudit("LOGOUT", currentUser.getUsername(), "Owner logged out");
        }
        currentUser = null;
    }

    public static Optional<User> getCurrentUser() {
        return Optional.ofNullable(currentUser);
    }

    public static boolean isLoggedIn() {
        return currentUser != null;
    }

    public void changePassword(Long userId, String currentPassword, String newPassword) {
        if (newPassword == null || newPassword.length() < 6) {
            throw new AuthenticationException("New password must be at least 6 characters.");
        }
        SessionManager.withTransactionVoid(session -> {
            User user = session.get(User.class, userId);
            if (user == null) {
                throw new AuthenticationException("User not found.");
            }
            if (!PasswordUtil.matches(currentPassword, user.getPasswordHash())) {
                throw new AuthenticationException("Current password is incorrect.");
            }
            user.setPasswordHash(PasswordUtil.hash(newPassword));
            user.setMustChangePassword(false);
            session.merge(user);
        });
        if (currentUser != null && currentUser.getId().equals(userId)) {
            currentUser.setMustChangePassword(false);
        }
        writeAudit("CHANGE_PASSWORD", "User#" + userId, "Password changed");
    }

    private void writeAudit(String action, String who, String details) {
        try {
            SessionManager.withTransactionVoid(session ->
                    session.persist(new AuditLog(action, "User", who, details, who)));
        } catch (Exception e) {
            log.warn("Failed to write audit log for action {}", action, e);
        }
    }
}
