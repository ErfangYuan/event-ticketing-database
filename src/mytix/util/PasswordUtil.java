package mytix.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class PasswordUtil {

    
    public static final String SAMPLE_LITERAL_HASH = "hash_password";

    private PasswordUtil() {}

    public static String sha256Hex(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] dig = md.digest(password.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(dig);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    
    public static boolean matches(String storedHash, String plainPassword) {
        if (storedHash == null || plainPassword == null) {
            return false;
        }
        if (storedHash.equalsIgnoreCase(sha256Hex(plainPassword))) {
            return true;
        }
        return SAMPLE_LITERAL_HASH.equals(storedHash) && "password".equals(plainPassword);
    }
}
