package mytix.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class PasswordUtil {

    private static final String PREFIX = "pbkdf2-sha256$v1$";
    private static final int ITERATIONS = 600_000;
    private static final int MAX_VERIFY_ITERATIONS = 2_000_000;
    private static final int SALT_BYTES = 16;
    private static final int KEY_BYTES = 32;
    public static final int MAX_PASSWORD_LENGTH = 1024;
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordUtil() {}

    public static String hash(String password) {
        if (password == null || password.isBlank() || password.length() > MAX_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("Password must contain 1 to 1024 characters and not be blank.");
        }
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        byte[] key = derive(password, salt, ITERATIONS);
        try {
            return PREFIX + ITERATIONS + "$" + Base64.getEncoder().encodeToString(salt)
                    + "$" + Base64.getEncoder().encodeToString(key);
        } finally {
            Arrays.fill(key, (byte) 0);
        }
    }

    /** Only verifies historical unsalted hashes; never use this to store new passwords. */
    private static byte[] legacySha256(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return md.digest(password.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public static boolean matches(String storedHash, String plainPassword) {
        if (storedHash == null || plainPassword == null || plainPassword.isBlank()
                || plainPassword.length() > MAX_PASSWORD_LENGTH || storedHash.length() > 255) {
            return false;
        }
        if (isLegacyHash(storedHash)) {
            return MessageDigest.isEqual(HexFormat.of().parseHex(storedHash), legacySha256(plainPassword));
        }
        try {
            String[] fields = storedHash.split("\\$", -1);
            if (fields.length != 5 || !storedHash.startsWith(PREFIX)
                    || !fields[2].matches("[0-9]{1,7}")) {
                return false;
            }
            int iterations = Integer.parseInt(fields[2]);
            if (iterations < 100_000 || iterations > MAX_VERIFY_ITERATIONS) {
                return false;
            }
            byte[] salt = Base64.getDecoder().decode(fields[3]);
            byte[] expected = Base64.getDecoder().decode(fields[4]);
            if (salt.length != SALT_BYTES || expected.length != KEY_BYTES) {
                return false;
            }
            byte[] actual = derive(plainPassword, salt, iterations);
            try {
                return MessageDigest.isEqual(expected, actual);
            } finally {
                Arrays.fill(actual, (byte) 0);
            }
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** Call only after successful verification; successful logins can upgrade old work factors. */
    public static boolean needsUpgrade(String storedHash) {
        if (isLegacyHash(storedHash)) {
            return true;
        }
        if (storedHash == null || !storedHash.startsWith(PREFIX)) {
            return false;
        }
        try {
            return Integer.parseInt(storedHash.split("\\$", -1)[2]) < ITERATIONS;
        } catch (IllegalArgumentException | IndexOutOfBoundsException e) {
            return false;
        }
    }

    private static boolean isLegacyHash(String value) {
        return value != null && value.matches("[0-9a-fA-F]{64}");
    }

    private static byte[] derive(String password, byte[] salt, int iterations) {
        char[] chars = password.toCharArray();
        PBEKeySpec spec = new PBEKeySpec(chars, salt, iterations, KEY_BYTES * 8);
        Arrays.fill(chars, '\0');
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("Password hashing is unavailable.", e);
        } finally {
            spec.clearPassword();
        }
    }
}
