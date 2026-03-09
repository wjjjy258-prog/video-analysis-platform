package com.video.analysis.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Locale;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class PasswordUtil {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String PBKDF2_PREFIX = "pbkdf2";
    private static final int PBKDF2_ITERATIONS = 120_000;
    private static final int PBKDF2_KEY_LENGTH_BITS = 256;

    private PasswordUtil() {
    }

    public static String randomSaltHex() {
        byte[] bytes = new byte[16];
        SECURE_RANDOM.nextBytes(bytes);
        return toHex(bytes);
    }

    public static String hashPassword(String rawPassword, String saltHex) {
        String pwd = rawPassword == null ? "" : rawPassword;
        byte[] salt = decodeSalt(saltHex);
        byte[] derived = pbkdf2(pwd, salt, PBKDF2_ITERATIONS);
        return PBKDF2_PREFIX + "$" + PBKDF2_ITERATIONS + "$" + toHex(derived);
    }

    public static boolean verify(String rawPassword, String saltHex, String storedHash) {
        if (storedHash == null || storedHash.isBlank()) {
            return false;
        }
        String normalized = storedHash.trim();
        if (normalized.toLowerCase(Locale.ROOT).startsWith(PBKDF2_PREFIX + "$")) {
            return verifyPbkdf2(rawPassword, saltHex, normalized);
        }
        return verifyLegacySha256(rawPassword, saltHex, normalized);
    }

    public static boolean needsRehash(String storedHash) {
        if (storedHash == null || storedHash.isBlank()) {
            return true;
        }
        return !storedHash.trim().toLowerCase(Locale.ROOT).startsWith(PBKDF2_PREFIX + "$");
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

    private static boolean verifyLegacySha256(String rawPassword, String saltHex, String storedHash) {
        String pwd = rawPassword == null ? "" : rawPassword;
        String salt = saltHex == null ? "" : saltHex;
        byte[] expected = decodeHex(storedHash);
        byte[] actual = sha256((salt + ":" + pwd).getBytes(StandardCharsets.UTF_8));
        return expected.length > 0 && MessageDigest.isEqual(expected, actual);
    }

    private static boolean verifyPbkdf2(String rawPassword, String saltHex, String storedHash) {
        String[] parts = storedHash.split("\\$");
        if (parts.length != 3) {
            return false;
        }
        int iterations;
        try {
            iterations = Integer.parseInt(parts[1]);
        } catch (NumberFormatException ex) {
            return false;
        }
        byte[] expected = decodeHex(parts[2]);
        if (expected.length == 0) {
            return false;
        }
        byte[] actual = pbkdf2(rawPassword == null ? "" : rawPassword, decodeSalt(saltHex), iterations);
        return MessageDigest.isEqual(expected, actual);
    }

    private static byte[] pbkdf2(String password, byte[] salt, int iterations) {
        char[] chars = password.toCharArray();
        PBEKeySpec spec = new PBEKeySpec(chars, salt, Math.max(1, iterations), PBKDF2_KEY_LENGTH_BITS);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to hash password.", ex);
        } finally {
            spec.clearPassword();
        }
    }

    private static byte[] decodeSalt(String saltHex) {
        if (saltHex == null || saltHex.isBlank()) {
            return new byte[0];
        }
        byte[] decoded = decodeHex(saltHex.trim());
        if (decoded.length > 0) {
            return decoded;
        }
        return saltHex.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] decodeHex(String value) {
        if (value == null) {
            return new byte[0];
        }
        String hex = value.trim();
        if (hex.length() % 2 != 0 || hex.isEmpty()) {
            return new byte[0];
        }
        byte[] bytes = new byte[hex.length() / 2];
        for (int i = 0; i < hex.length(); i += 2) {
            int hi = Character.digit(hex.charAt(i), 16);
            int lo = Character.digit(hex.charAt(i + 1), 16);
            if (hi < 0 || lo < 0) {
                return new byte[0];
            }
            bytes[i / 2] = (byte) ((hi << 4) + lo);
        }
        return bytes;
    }
}
