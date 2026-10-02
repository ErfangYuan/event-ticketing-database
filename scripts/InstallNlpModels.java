import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Properties;

/** Run from the repository root with: java scripts/InstallNlpModels.java [directory]. */
public final class InstallNlpModels {
    public static void main(String[] args) throws Exception {
        if (args.length > 1) {
            throw new IllegalArgumentException(
                    "Usage: java scripts/InstallNlpModels.java [model-directory]");
        }
        Path manifest = Path.of("scripts", "opennlp-models.properties");
        if (!Files.isRegularFile(manifest)) {
            throw new IOException("Run this command from the repository root: "
                    + "java scripts/InstallNlpModels.java");
        }
        Path directory = args.length == 1 ? Path.of(args[0]) : Path.of("src/lib/opennlp");
        install(manifest, directory);
        System.out.println("OpenNLP models ready: " + directory.toAbsolutePath());
    }

    static void install(Path manifest, Path directory) throws Exception {
        Properties properties = new Properties();
        try (var input = Files.newInputStream(manifest)) {
            properties.load(input);
        }
        Files.createDirectories(directory);
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        for (String model : properties.getProperty("models", "").split(",")) {
            model = model.trim();
            if (!model.matches("[a-z0-9-]+\\.bin")) {
                throw new IOException("Invalid model filename in manifest: " + model);
            }
            String expected = properties.getProperty(model + ".sha256", "");
            URI uri = URI.create(properties.getProperty(model + ".url", ""));
            if (!expected.matches("[a-f0-9]{64}") || !"https".equals(uri.getScheme())) {
                throw new IOException("Expected an HTTPS URL and SHA-256 for " + model);
            }
            Path target = directory.resolve(model);
            if (Files.isRegularFile(target) && expected.equals(sha256(target))) {
                System.out.println("Verified existing " + model);
                continue;
            }
            Path temporary = Files.createTempFile(directory, model + "-", ".part");
            try {
                System.out.println("Downloading " + model);
                HttpRequest request = HttpRequest.newBuilder(uri)
                        .timeout(Duration.ofSeconds(60)).GET().build();
                var response = client.send(request, HttpResponse.BodyHandlers.ofFile(temporary));
                if (response.statusCode() != 200) {
                    throw new IOException("HTTP " + response.statusCode() + " downloading " + model);
                }
                if (!expected.equals(sha256(temporary))) {
                    throw new IOException("SHA-256 mismatch for " + model
                            + "; existing file preserved. Check the upstream source and manifest.");
                }
                try {
                    Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
                }
                System.out.println("Installed verified " + model);
            } finally {
                Files.deleteIfExists(temporary);
            }
        }
    }

    private static String sha256(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (var input = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            int size;
            while ((size = input.read(buffer)) != -1) {
                digest.update(buffer, 0, size);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
