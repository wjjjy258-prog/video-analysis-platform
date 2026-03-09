package com.video.analysis.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

public final class PasswordUtil {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private PasswordUtil() {
    }

    public static String randomSaltHex() {
        byte[] bytes = new byte[16];
        SECURE_RANDOM.nextBytes(bytes);
        return toHex(bytes);
    }

    public static String hashPassword(String rawPassword, String saltHex) {
        String pwd = rawPassword == null ? "" : rawPassword;
        String salt = saltHex == null ? "" : saltHex;
        byte[] bytes = sha256((salt + ":" + pwd).getBytes(StandardCharsets.UTF_8));
        return toHex(bytes);
    }

    public static boolean verify(String rawPassword, String saltHex, String storedHash) {
        if (storedHash == null || storedHash.isBlank()) {
            return false;
        }
        return hashPassword(rawPassword, saltHex).equalsIgnoreCase(storedHash.trim());
    }

    private static byte[] sha256(byte[] payload) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(payload);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to hash password.", ex);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
