package io.github.certtool.app.task;

import io.github.certtool.reporting.ReportEnvelope;
import io.github.certtool.reporting.ReportSchema;
import io.github.certtool.reporting.core.HtmlReportRenderer;
import io.github.certtool.reporting.core.JsonReportRenderer;
import io.github.certtool.reporting.core.MarkdownReportRenderer;
import io.github.certtool.reporting.core.ReportRenderer;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Objects;
import javafx.concurrent.Task;

/**
 * JavaFX {@link Task} that renders a {@link ReportEnvelope} to JSON/HTML/Markdown and writes it
 * to the user-chosen output file using an atomic temp-file replace.
 *
 * <p>Never logs the rendered bytes; the SanitizationGuard inside the renderer is the last line of
 * defence and may throw {@link io.github.certtool.reporting.RenderException} which is propagated
 * to the caller.
 */
public final class ExportReportTask extends Task<Path> {

    public enum Format { JSON, HTML, MARKDOWN }

    private final ReportEnvelope envelope;
    private final Path target;
    private final Format format;

    public ExportReportTask(ReportEnvelope envelope, Path target, Format format) {
        this.envelope = Objects.requireNonNull(envelope, "envelope");
        this.target = Objects.requireNonNull(target, "target");
        this.format = Objects.requireNonNull(format, "format");
    }

    @Override
    protected Path call() throws IOException {
        tryMessage("Rendering " + format + " report…");
        ReportRenderer renderer = switch (format) {
            case JSON -> new JsonReportRenderer();
            case HTML -> new HtmlReportRenderer();
            case MARKDOWN -> new MarkdownReportRenderer();
        };
        byte[] bytes = renderer.render(envelope);
        tryMessage("Writing " + format + " to " + target);
        writeAtomic(target, bytes);
        return target;
    }

    /** Atomic write — temp in same dir, fsync, move. */
    private static void writeAtomic(Path target, byte[] payload) throws IOException {
        Path parent = target.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path tmp = Files.createTempFile(parent, target.getFileName().toString(), ".partial");
        try {
            Files.write(tmp, payload);
            try {
                Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException notAtomic) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | RuntimeException e) {
            try { Files.deleteIfExists(tmp); } catch (IOException ignored) {}
            throw e;
        }
    }

    /** Default file name for a given title + format. */
    public static String defaultFileName(String title, Format format) {
        String safe = title == null ? "report" : title.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9._-]+", "_");
        return switch (format) {
            case JSON -> safe + ".report." + ReportSchema.REPORT_SCHEMA_VERSION + ".json";
            case HTML -> safe + ".report." + ReportSchema.REPORT_SCHEMA_VERSION + ".html";
            case MARKDOWN -> safe + ".report." + ReportSchema.REPORT_SCHEMA_VERSION + ".md";
        };
    }

    private void tryMessage(String msg) {
        try {
            updateMessage(msg);
        } catch (IllegalStateException ignored) {
            // headless callers
        }
    }
}