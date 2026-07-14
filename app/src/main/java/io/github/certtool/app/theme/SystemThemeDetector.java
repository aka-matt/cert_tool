package io.github.certtool.app.theme;

import java.util.Locale;
import java.util.Objects;

/**
 * Pure-Java helper for detecting the operating-system's preferred theme on Windows, macOS, and
 * Linux. Does NOT depend on JavaFX so the platform module can use it for headless checks.
 *
 * <p>Detection is best-effort — call sites MUST handle the "unknown" case (return null) and fall
 * back to the configured default.
 */
public final class SystemThemeDetector {

    private SystemThemeDetector() {}

    /**
     * Returns {@code "dark"} if the system prefers dark mode, {@code "light"} otherwise, or
     * {@code null} if detection is not possible on the current OS.
     */
    public static String detect() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            return detectWindows();
        }
        if (os.contains("mac")) {
            return detectMac();
        }
        if (os.contains("nix") || os.contains("nux") || os.contains("aix")) {
            return detectLinux();
        }
        return null;
    }

    private static String detectWindows() {
        try {
            Process p = new ProcessBuilder("reg", "query",
                    "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
                    "/v", "AppsUseLightTheme")
                    .redirectErrorStream(true)
                    .start();
            byte[] out;
            try (var in = p.getInputStream()) {
                out = in.readAllBytes();
            }
            p.waitFor();
            String s = new String(out, java.nio.charset.StandardCharsets.UTF_8);
            // 0x0 → light, 0x1 → dark
            if (s.contains("0x0")) {
                return "light";
            }
            if (s.contains("0x1")) {
                return "dark";
            }
        } catch (Exception ignored) {
            // best-effort
        }
        return null;
    }

    private static String detectMac() {
        try {
            Process p = new ProcessBuilder("defaults", "read", "-g", "AppleInterfaceStyle")
                    .redirectErrorStream(true)
                    .start();
            byte[] out;
            try (var in = p.getInputStream()) {
                out = in.readAllBytes();
            }
            p.waitFor();
            String s = new String(out, java.nio.charset.StandardCharsets.UTF_8).trim();
            if (s.equalsIgnoreCase("Dark")) {
                return "dark";
            }
            return "light";
        } catch (Exception ignored) {
            // best-effort
        }
        return null;
    }

    private static String detectLinux() {
        // GNOME / KDE / XFCE all honour this.
        String desktop = System.getenv("XDG_CURRENT_DESKTOP");
        String gsettings = System.getenv("GSETTINGS_BACKEND");
        if (desktop == null && gsettings == null) {
            return null;
        }
        try {
            Process p = new ProcessBuilder("gsettings", "get",
                    "org.gnome.desktop.interface", "color-scheme")
                    .redirectErrorStream(true)
                    .start();
            byte[] out;
            try (var in = p.getInputStream()) {
                out = in.readAllBytes();
            }
            p.waitFor();
            String s = new String(out, java.nio.charset.StandardCharsets.UTF_8).trim();
            if (s.contains("dark")) {
                return "dark";
            }
            if (s.contains("light") || s.contains("default") || s.contains("no-preference")) {
                return "light";
            }
        } catch (Exception ignored) {
            // best-effort
        }
        return null;
    }

    /** Convenience for tests — maps the detector's output to {@link ThemeMode}. */
    public static ThemeMode resolveSystemTheme() {
        String r = Objects.requireNonNullElse(detect(), "light");
        return "dark".equals(r) ? ThemeMode.DARK : ThemeMode.LIGHT;
    }
}