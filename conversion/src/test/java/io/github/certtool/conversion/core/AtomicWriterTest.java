package io.github.certtool.conversion.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("AtomicWriter")
class AtomicWriterTest {

    @Test
    @DisplayName("writes the bytes to the target file atomically")
    void atomicMoveHappyPath(@TempDir Path dir) throws IOException {
        Path target = dir.resolve("target.bin");
        byte[] payload = "hello world".getBytes(StandardCharsets.UTF_8);

        long bytes = AtomicWriter.write(target, payload);

        assertThat(bytes).isEqualTo(payload.length);
        assertThat(Files.exists(target)).isTrue();
        assertThat(Files.readAllBytes(target)).isEqualTo(payload);
        // No leftover temp files in the directory.
        try (Stream<Path> entries = Files.list(dir)) {
            assertThat(entries).containsExactly(target);
        }
    }

    @Test
    @DisplayName("does not create the target file when the temp write fails")
    void noLeftoverTargetOnFailure(@TempDir Path dir) throws IOException {
        Path target = dir.resolve("missing-parent/target.bin");
        // Parent directory does not exist — Files.write will fail.
        byte[] payload = "x".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> AtomicWriter.write(target, payload))
                .isInstanceOf(IOException.class);

        // Original target must not exist.
        assertThat(Files.exists(target)).isFalse();
    }

    @Test
    @DisplayName("overwrites an existing target by atomically replacing it")
    void overwriteExisting(@TempDir Path dir) throws IOException {
        Path target = dir.resolve("target.bin");
        Files.write(target, "old".getBytes(StandardCharsets.UTF_8));

        byte[] payload = "new".getBytes(StandardCharsets.UTF_8);
        AtomicWriter.write(target, payload);

        assertThat(Files.readAllBytes(target)).isEqualTo(payload);
    }

    @Test
    @DisplayName("rejects empty bytes array with IllegalArgumentException")
    void rejectsEmptyBytes(@TempDir Path dir) {
        Path target = dir.resolve("target.bin");
        assertThatThrownBy(() -> AtomicWriter.write(target, new byte[0]))
                .isInstanceOf(IllegalArgumentException.class);
    }
}