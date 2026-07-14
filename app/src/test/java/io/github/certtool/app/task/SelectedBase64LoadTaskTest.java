package io.github.certtool.app.task;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.certtool.domain.keystore.KeyStoreContainerType;
import io.github.certtool.domain.load.KeyStoreLoadResult;
import io.github.certtool.keystorecore.password.FixedPasswordProvider;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SelectedBase64LoadTask")
class SelectedBase64LoadTaskTest {

    @Test
    @DisplayName("call() decodes and loads the selected container")
    void callDecodesAndLoadsTheSelectedContainer() throws Exception {
        AtomicReference<KeyStoreContainerType> selected = new AtomicReference<>();
        SelectedBase64LoadTask task = new SelectedBase64LoadTask(
                (bytes, container, passwords) -> {
                    selected.set(container);
                    return KeyStoreLoadResult.success(container, "test", "1", List.of());
                },
                Base64.getEncoder().encodeToString(new byte[] {1, 2, 3}),
                KeyStoreContainerType.BCFKS,
                new FixedPasswordProvider(new char[0], Map.of()));

        KeyStoreLoadResult result = task.call();

        assertThat(selected.get()).isEqualTo(KeyStoreContainerType.BCFKS);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.container()).isEqualTo(KeyStoreContainerType.BCFKS);
    }
}
