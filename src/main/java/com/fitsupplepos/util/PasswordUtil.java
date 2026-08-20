package com.fitsupplepos.util;

import at.favre.lib.crypto.bcrypt.BCrypt;

/** Thin wrapper around the BCrypt library — never store or compare plaintext passwords. */
public final class PasswordUtil {

    private static final int COST_FACTOR = 12;

    private PasswordUtil() {}

    public static String hash(String plainPassword) {
        return BCrypt.withDefaults().hashToString(COST_FACTOR, plainPassword.toCharArray());
    }

    public static boolean matches(String plainPassword, String hash) {
        if (plainPassword == null || hash == null || hash.isBlank()) {
            return false;
        }
        BCrypt.Result result = BCrypt.verifyer().verify(plainPassword.toCharArray(), hash);
        return result.verified;
    }
}
