package io.github.certtool.app.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.certtool.domain.certificate.BasicConstraintsInfo;
import io.github.certtool.domain.certificate.CertificateAnalysis;
import io.github.certtool.domain.certificate.ExtensionAnalysis;
import io.github.certtool.domain.certificate.FingerprintBundle;
import io.github.certtool.domain.certificate.KeyAlgorithm;
import io.github.certtool.domain.certificate.KeyUsageBits;
import io.github.certtool.domain.certificate.PublicKeyInfo;
import io.github.certtool.domain.certificate.SelfSignedStatus;
import io.github.certtool.domain.certificate.ValidityState;
import io.github.certtool.domain.certificate.ValidityWindow;
import io.github.certtool.domain.inspect.InspectedCertificate;
import io.github.certtool.domain.inspect.InspectedEntry;
import io.github.certtool.domain.inspect.InspectedKeyStore;
import io.github.certtool.domain.inspect.KeyStoreSummary;
import io.github.certtool.domain.keystore.ContentEncoding;
import io.github.certtool.domain.keystore.EntryType;
import io.github.certtool.domain.keystore.KeyStoreContainerType;
import io.github.certtool.domain.load.KeyStoreLoadResult;
import java.math.BigInteger;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("InspectViewModel")
class InspectViewModelTest {

    private static InspectedEntry entry(String alias) {
        return new InspectedEntry(alias, EntryType.TRUSTED_CERTIFICATE,
                new Date(), true, null, null, List.of(), List.of());
    }

    private static InspectedCertificate dummyCert(int chainIndex) {
        ValidityWindow v = new ValidityWindow(Instant.EPOCH, Instant.EPOCH.plusSeconds(60));
        return new InspectedCertificate(chainIndex, new CertificateAnalysis(
                "CN=x", "CN=x", BigInteger.ZERO, "0", "0",
                3, v, ValidityState.VALID, "SHA256withRSA", "1.2.3.4.5",
                new PublicKeyInfo(KeyAlgorithm.UNKNOWN, null, null, null, null),
                new ExtensionAnalysis(
                        BasicConstraintsInfo.absent(), KeyUsageBits.empty(),
                        List.of(), List.of(), List.of(),
                        null, null,
                        List.of(), List.of(), List.of(), List.of(),
                        List.of(), List.of(), List.of()),
                new FingerprintBundle("", "", "", ""),
                new SelfSignedStatus(false, false),
                ""));
    }

    @Test
    @DisplayName("setInspected auto-selects the first entry's alias when nothing was selected")
    void setInspectedAutoSelects() {
        InspectViewModel vm = new InspectViewModel();
        InspectedKeyStore inspected = new InspectedKeyStore(
                KeyStoreSummary.from(
                        KeyStoreLoadResult.success(KeyStoreContainerType.JKS, "SUN", "17", List.of()),
                        ContentEncoding.BINARY),
                List.of(entry("first"), entry("second")));

        vm.setInspected(inspected);

        assertThat(vm.getInspected()).isSameAs(inspected);
        assertThat(vm.getSelectedAlias()).isEqualTo("first");
        assertThat(vm.getCurrentEntry()).isNotNull();
        assertThat(vm.getCurrentEntry().alias()).isEqualTo("first");
    }

    @Test
    @DisplayName("currentEntry follows selectedAlias changes after inspection is set")
    void currentEntryFollowsSelection() {
        InspectViewModel vm = new InspectViewModel();
        InspectedKeyStore inspected = new InspectedKeyStore(
                KeyStoreSummary.from(
                        KeyStoreLoadResult.success(KeyStoreContainerType.JKS, "SUN", "17", List.of()),
                        ContentEncoding.BINARY),
                List.of(entry("a"), entry("b")));
        vm.setInspected(inspected);

        vm.setSelectedAlias("b");

        assertThat(vm.getCurrentEntry().alias()).isEqualTo("b");
        assertThat(vm.getCurrentCertificateIndex()).isZero();
    }

    @Test
    @DisplayName("switching aliases resets the current certificate index")
    void certIndexResetsOnAliasSwitch() {
        InspectViewModel vm = new InspectViewModel();
        InspectedEntry a = new InspectedEntry("a", EntryType.PRIVATE_KEY, new Date(), true,
                "RSA", 2048,
                List.of(dummyCert(0), dummyCert(1)),
                List.of());
        InspectedEntry b = entry("b");
        vm.setInspected(new InspectedKeyStore(
                KeyStoreSummary.from(
                        KeyStoreLoadResult.success(KeyStoreContainerType.JKS, "SUN", "17", List.of()),
                        ContentEncoding.BINARY),
                List.of(a, b)));

        vm.incrementCurrentCertificateIndex();
        vm.incrementCurrentCertificateIndex();
        assertThat(vm.getCurrentCertificateIndex()).isEqualTo(2);

        vm.setSelectedAlias("b");
        assertThat(vm.getCurrentCertificateIndex()).isZero();
    }

    @Test
    @DisplayName("currentEntry is null when no entry matches the selected alias")
    void currentEntryNullWhenUnmatched() {
        InspectViewModel vm = new InspectViewModel();
        vm.setInspected(new InspectedKeyStore(
                KeyStoreSummary.from(
                        KeyStoreLoadResult.success(KeyStoreContainerType.JKS, "SUN", "17", List.of()),
                        ContentEncoding.BINARY),
                List.of(entry("a"))));
        vm.setSelectedAlias("not-in-store");

        assertThat(vm.getCurrentEntry()).isNull();
    }
}
