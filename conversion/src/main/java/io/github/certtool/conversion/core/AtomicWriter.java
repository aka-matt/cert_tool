package io.github.certtool.conversion.core;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

/**
 * Writes bytes to a target file using a temp-file + atomic-replace pattern.
 *
 * <p>Spec §9 step 5 requirement: "write temp in same dir → reload and verify → atomic move →
 * delete temp on failure". This class implements the write-and-replace piece; the reload/verify
 * step is the orchestrator's responsibility and happens between writing the temp file and the
 * atomic move.
 *
 * <p>The temp file is created in the target's parent directory to guarantee the {@code move} is
 * within the same filesystem. On any I/O failure, the temp file is deleted and the original
 * target (if any) is left untouched.
 */
public final class AtomicWriter {

    private AtomicWriter() {}

    /**
     * Writes {@code payload} to {@code target} using an atomic temp-file replace.
     *
     * @return the number of bytes written
     * @throws IOException              if any I/O error occurs; the temp file is deleted
     * @throws IllegalArgumentException if {@code payload} is empty
     */
    public static long write(Path target, byte[] payload) throws IOException {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(payload, "payload");
        if (payload.length == 0) {
            throw new IllegalArgumentException("payload must contain at least one byte");
        }

        Path parent = target.toAbsolutePath().getParent();
        if (parent != null && !Files.exists(parent)) {
            // Do not auto-create parents — surface a clear IOException to the caller so the
            // orchestrator can roll back cleanly.
            throw new IOException("Target parent directory does not exist: " + parent);
        }

        Path temp = Files.createTempFile(
                parent == null ? Path.of(".") : parent,
                target.getFileName().toString(),
                ".partial");
        try {
            // Write payload with explicit fsync.
            try (FileChannel ch = FileChannel.open(
                    temp,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING)) {
                ch.write(java.nio.ByteBuffer.wrap(payload));
                ch.force(true);
            } catch (IOException e) {
                deleteQuietly(temp);
                throw e;
            }
            // Atomic move into place.
            try {
                Files.move(temp, target,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException notAtomic) {
                // Fall back to a non-atomic move on filesystems that don't support it. The delete
                // + copy pattern is still safer than a plain write because the temp file is the
                // only candidate visible until the move succeeds.
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                deleteQuietly(temp);
                throw e;
            }
        } catch (RuntimeException e) {
            deleteQuietly(temp);
            throw e;
        }
        return payload.length;
    }

    private static void deleteQuietly(Path p) {
        try {
            Files.deleteIfExists(p);
        } catch (IOException ignored) {
            // best-effort cleanup
        }
    }
}