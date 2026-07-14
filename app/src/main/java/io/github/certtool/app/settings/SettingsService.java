package io.github.certtool.app.settings;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads and writes {@link Settings} as JSON to {@code ~/.cert-tool/settings.json}.
 *
 * <p>Defensive in three ways:
 * <ol>
 *   <li>Missing file → {@link Settings#defaults()}.</li>
 *   <li>Corrupted file → {@link Settings#defaults()} (logs a WARN, never throws).</li>
 *   <li>Forbidden secret substrings in the serialized JSON → {@link IOException}.</li>
 * </ol>
 *
 * <p>The forbidden-substrings check mirrors {@link io.github.certtool.reporting.SanitizationGuard}
 * but is narrower: settings JSON contains user-controlled paths and profile names only; password
 * markers would only appear via a bug elsewhere.
 */
public final class SettingsService {

    private static final Logger LOG = LoggerFactory.getLogger(SettingsService.class);

    /** Forbidden substrings in persisted settings — checked by the same rules as reports. */
    private static final String[] FORBIDDEN = new String[]{
            "password=", "password:",
            "-----BEGIN PRIVATE KEY-----",
            "-----BEGIN RSA PRIVATE KEY-----",
            "-----BEGIN ENCRYPTED PRIVATE KEY-----",
            "FIPS Certification",
            "Official FIPS Validation",
            "NIST Certified",
            "正式认证结论"
    };

    private final Path file;
    private final ObjectMapper mapper;

    public SettingsService(Path file) {
        this.file = Objects.requireNonNull(file, "file");
        this.mapper = new ObjectMapper()
                .enable(SerializationFeature.INDENT_OUTPUT);
    }

    /** Returns a {@link SettingsService} targeting the default {@code ~/.cert-tool/settings.json}. */
    public static SettingsService defaults() {
        Path dir = Path.of(System.getProperty("user.home", "."), ".cert-tool");
        return new SettingsService(dir.resolve("settings.json"));
    }

    /**
     * Loads the persisted settings. Returns defaults if the file is missing or corrupted.
     *
     * @throws IOException if the file is present but unreadable at the OS level
     */
    public Settings load() throws IOException {
        if (!Files.exists(file)) {
            return Settings.defaults();
        }
        try {
            byte[] bytes = Files.readAllBytes(file);
            String text = new String(bytes, StandardCharsets.UTF_8);
            for (String pattern : FORBIDDEN) {
                if (text.contains(pattern)) {
                    throw new IOException("Persisted settings contain forbidden pattern: " + pattern);
                }
            }
            return mapper.readValue(bytes, Settings.class);
        } catch (com.fasterxml.jackson.core.JacksonException e) {
            LOG.warn("Corrupted settings file {} — falling back to defaults", file, e);
            return Settings.defaults();
        }
    }

    /**
     * Persists {@code settings} to the configured file, atomically. Creates parent dirs if needed.
     *
     * @throws IOException if the JSON contains forbidden secret substrings, or on I/O failure
     */
    public void save(Settings settings) throws IOException {
        Objects.requireNonNull(settings, "settings");
        String json = mapper.writeValueAsString(settings);
        for (String pattern : FORBIDDEN) {
            if (json.contains(pattern)) {
                throw new IOException("Refusing to save settings containing forbidden pattern: "
                        + pattern);
            }
        }
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path tmp = Files.createTempFile(parent, "settings", ".partial");
        try {
            Files.writeString(tmp, json, StandardCharsets.UTF_8);
            Files.move(tmp, file,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException notAtomic) {
            Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | RuntimeException e) {
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException ignored) {
                // best-effort cleanup
            }
            throw e;
        }
    }
}