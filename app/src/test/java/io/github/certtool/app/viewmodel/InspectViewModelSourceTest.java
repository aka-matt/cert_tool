package io.github.certtool.app.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.certtool.domain.keystore.ContentEncoding;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("InspectViewModel source tracking")
class InspectViewModelSourceTest {

    @Test
    @DisplayName("contentEncoding and sourcePath default to null / empty and round-trip values")
    void sourceRoundTrip() {
        InspectViewModel vm = new InspectViewModel();

        assertThat(vm.getContentEncoding()).isNull();
        assertThat(vm.getSourcePath()).isEmpty();

        vm.setContentEncoding(ContentEncoding.BINARY);
        vm.setSourcePath("/tmp/store.jks");

        assertThat(vm.getContentEncoding()).isEqualTo(ContentEncoding.BINARY);
        assertThat(vm.getSourcePath()).isEqualTo("/tmp/store.jks");

        vm.setSourcePath(null);
        assertThat(vm.getSourcePath()).isEmpty();
    }
}