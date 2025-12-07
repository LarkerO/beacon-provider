package com.hydroline.beacon.provider.protocol;

import java.security.SecureRandom;

/**
 * Utility for producing and validating fixed-length request identifiers shared by Bukkit and the mod.
 */
public final class RequestId {
    public static final int LENGTH = 12;
    private static final char[] ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyz".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private RequestId() {
    }

    public static String random() {
        return random(LENGTH);
    }

    public static String random(int length) {
        if (length <= 0) {
            throw new IllegalArgumentException("length must be > 0");
        }
        char[] buffer = new char[length];
        for (int i = 0; i < length; i++) {
            buffer[i] = ALPHABET[RANDOM.nextInt(ALPHABET.length)];
        }
        return new String(buffer);
    }

    public static boolean isValid(String value) {
        if (value == null || value.length() != LENGTH) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = Character.toLowerCase(value.charAt(i));
            if ((c < '0' || c > '9') && (c < 'a' || c > 'z')) {
                return false;
            }
        }
        return true;
    }
}
