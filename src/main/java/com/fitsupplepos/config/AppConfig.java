package com.fitsupplepos.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * External, non-source-controlled configuration: secrets (WhatsApp Cloud API token/phone
 * number ID) and machine-local preferences (session idle-timeout). Lives at
 * {@code <FITSUPPLE_HOME>/config/app.properties}, created with sane defaults on first run.
 *
 * Deliberately separate from {@code src/main/resources/application.properties}, which is
 * source-controlled and must never contain secrets.
 */
public final class AppConfig {

    private static final Logger log = LoggerFactory.getLogger(AppConfig.class);

    private static final String KEY_SESSION_TIMEOUT = "session.timeoutMinutes";
    private static final String KEY_WA_ENABLED = "whatsapp.enabled";
    private static final String KEY_WA_TOKEN = "whatsapp.accessToken";
    private static final String KEY_WA_PHONE_NUMBER_ID = "whatsapp.phoneNumberId";
    private static final String KEY_WA_BUSINESS_ACCOUNT_ID = "whatsapp.businessAccountId";
    private static final String KEY_WA_API_VERSION = "whatsapp.apiVersion";

    private static volatile Properties props;

    private AppConfig() {}

    private static synchronized Properties load() {
        if (props != null) return props;
        Properties p = new Properties();
        // Defaults — written out on first run so the owner has a real file to edit.
        p.setProperty(KEY_SESSION_TIMEOUT, "15");
        p.setProperty(KEY_WA_ENABLED, "false");
        p.setProperty(KEY_WA_TOKEN, "");
        p.setProperty(KEY_WA_PHONE_NUMBER_ID, "");
        p.setProperty(KEY_WA_BUSINESS_ACCOUNT_ID, "");
        p.setProperty(KEY_WA_API_VERSION, "v20.0");

        Path file = AppPaths.configFile();
        try {
            if (Files.exists(file)) {
                try (InputStream in = Files.newInputStream(file)) {
                    Properties onDisk = new Properties();
                    onDisk.load(in);
                    p.putAll(onDisk);
                }
            } else {
                Files.createDirectories(file.getParent());
                try (OutputStream out = Files.newOutputStream(file)) {
                    p.store(out, "FitSupple POS — local configuration & secrets. Do not commit this file.");
                }
                log.info("Created default config file at {}", file);
            }
        } catch (IOException e) {
            log.warn("Could not read/create external config file at {} — using in-memory defaults.", file, e);
        }
        props = p;
        return p;
    }

    /** Forces the next read to reload from disk (call after Settings screen writes changes). */
    public static synchronized void reload() {
        props = null;
    }

    public static void save(Properties updated) {
        Path file = AppPaths.configFile();
        try {
            Files.createDirectories(file.getParent());
            try (OutputStream out = Files.newOutputStream(file)) {
                updated.store(out, "FitSupple POS — local configuration & secrets. Do not commit this file.");
            }
        } catch (IOException e) {
            log.error("Failed to save config file at {}", file, e);
        }
        reload();
    }

    public static int sessionTimeoutMinutes() {
        try {
            return Integer.parseInt(load().getProperty(KEY_SESSION_TIMEOUT, "15").trim());
        } catch (NumberFormatException e) {
            return 15;
        }
    }

    public static void setSessionTimeoutMinutes(int minutes) {
        Properties p = load();
        p.setProperty(KEY_SESSION_TIMEOUT, String.valueOf(Math.max(1, minutes)));
        save(p);
    }

    public static boolean whatsAppEnabled() {
        return Boolean.parseBoolean(load().getProperty(KEY_WA_ENABLED, "false"));
    }

    public static String whatsAppAccessToken() {
        return load().getProperty(KEY_WA_TOKEN, "");
    }

    public static String whatsAppPhoneNumberId() {
        return load().getProperty(KEY_WA_PHONE_NUMBER_ID, "");
    }

    public static String whatsAppBusinessAccountId() {
        return load().getProperty(KEY_WA_BUSINESS_ACCOUNT_ID, "");
    }

    public static String whatsAppApiVersion() {
        String v = load().getProperty(KEY_WA_API_VERSION, "v20.0");
        return (v == null || v.isBlank()) ? "v20.0" : v.trim();
    }

    public static void setWhatsAppCredentials(boolean enabled, String token, String phoneNumberId, String businessAccountId) {
        Properties p = load();
        p.setProperty(KEY_WA_ENABLED, String.valueOf(enabled));
        p.setProperty(KEY_WA_TOKEN, token == null ? "" : token.trim());
        p.setProperty(KEY_WA_PHONE_NUMBER_ID, phoneNumberId == null ? "" : phoneNumberId.trim());
        p.setProperty(KEY_WA_BUSINESS_ACCOUNT_ID, businessAccountId == null ? "" : businessAccountId.trim());
        save(p);
    }

    /** True once both a token and a phone number id have been configured. */
    public static boolean whatsAppConfigured() {
        return whatsAppEnabled() && !whatsAppAccessToken().isBlank() && !whatsAppPhoneNumberId().isBlank();
    }
}
