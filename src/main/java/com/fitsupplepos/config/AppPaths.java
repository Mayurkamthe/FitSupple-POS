package com.fitsupplepos.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Single source of truth for every on-disk location the app touches: the SQLite
 * database file, invoice PDFs, backups, logs, and the external (non-source-controlled)
 * config file that holds secrets like the WhatsApp API token.
 *
 * <p>The root "home" directory is resolved from the {@code FITSUPPLE_HOME} system
 * property or environment variable (the same variable {@code logback.xml} already
 * keys off of), falling back to the current working directory for local development.
 * A packaged install (see jpackage config in pom.xml) passes {@code -DFITSUPPLE_HOME=...}
 * pointing at a writable per-user app-data directory.
 */
public final class AppPaths {

    private AppPaths() {}

    public static Path homeDir() {
        String home = System.getProperty("FITSUPPLE_HOME", System.getenv("FITSUPPLE_HOME"));
        if (home == null || home.isBlank()) {
            home = ".";
        }
        return Paths.get(home).toAbsolutePath().normalize();
    }

    public static Path dataDir() {
        return homeDir().resolve("data");
    }

    public static Path backupDir() {
        return homeDir().resolve("backups");
    }

    public static Path logDir() {
        return homeDir().resolve("logs");
    }

    public static Path invoiceDir() {
        return dataDir().resolve("invoices");
    }

    public static Path configDir() {
        return homeDir().resolve("config");
    }

    public static Path configFile() {
        return configDir().resolve("app.properties");
    }

    public static Path databaseFile() {
        return dataDir().resolve("fitsupple.db");
    }

    /** Creates every directory this class points at, if missing. Idempotent. */
    public static void ensureDirectoriesExist() throws IOException {
        Files.createDirectories(dataDir());
        Files.createDirectories(backupDir());
        Files.createDirectories(logDir());
        Files.createDirectories(invoiceDir());
        Files.createDirectories(configDir());
    }
}
