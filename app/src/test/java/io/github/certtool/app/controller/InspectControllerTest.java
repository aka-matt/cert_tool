package io.github.certtool.app.controller;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.certtool.app.viewmodel.InspectViewModel;
import io.github.certtool.app.viewmodel.InspectViewModel.Group;
import io.github.certtool.app.viewmodel.InspectViewModel.NavNode;
import io.github.certtool.domain.keystore.ContentEncoding;
import io.github.certtool.domain.keystore.EntryType;
import io.github.certtool.domain.keystore.KeyStoreContainerType;
import io.github.certtool.domain.load.KeyStoreLoadResult;
import io.github.certtool.domain.load.LoadedEntry;
import io.github.certtool.testfixtures.CertificateGenerator;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import javax.security.auth.x500.X500Principal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("InspectController")
class InspectControllerTest {

    private static X509Certificate leafCert() throws Exception {
        return CertificateGenerator.selfSigned(
                new X500Principal("CN=leaf"),
                CertificateGenerator.rsaKeyPair(2048),
                "SHA256withRSA",
                Duration.ofDays(30));
    }

    @Test
    @DisplayName("onLoadResult populates the view-model and auto-selects the first node")
    void onLoadResultAutoSelects() throws Exception {
        X509Certificate cert = leafCert();
        KeyStoreLoadResult result = KeyStoreLoadResult.success(
                KeyStoreContainerType.JKS, "SUN", "17",
                List.of(LoadedEntry.trustedCertificate("a", cert, new Date())));
        InspectViewModel vm = new InspectViewModel();
        InspectController c = new InspectController(vm);

        c.onLoadResult(result);

        assertThat(vm.getLoadResult()).isSameAs(result);
        assertThat(vm.navNodes()).hasSize(1);
        assertThat(vm.getSelectedAlias()).isEqualTo("a");
    }

    @Test
    @DisplayName("groups entries by EntryType and sorts alphabetically within each group")
    void groupsByTypeAndSorts() throws Exception {
        X509Certificate cert = leafCert();
        KeyStoreLoadResult result = KeyStoreLoadResult.success(
                KeyStoreContainerType.JKS, "SUN", "17",
                List.of(
                        LoadedEntry.trustedCertificate("zeta", cert, new Date()),
                        LoadedEntry.trustedCertificate("alpha", cert, new Date()),
                        new LoadedEntry("k1", EntryType.SECRET_KEY, new Date(),
                                List.of(), "AES", 256, true, List.of()),
                        new LoadedEntry("k2", EntryType.PRIVATE_KEY, new Date(),
                                List.of(cert), "RSA", 2048, true, List.of()),
                        LoadedEntry.unknown("broken", "cannot decrypt")));
        InspectViewModel vm = new InspectViewModel();
        new InspectController(vm).onLoadResult(result);

        List<NavNode> nodes = vm.navNodes();
        // PRIVATE_KEYS (k2), TRUSTED_CERTIFICATES (alpha, zeta), SECRET_KEYS (k1), UNREADABLE (broken)
        assertThat(nodes).extracting(NavNode::alias).containsExactly(
                "k2", "alpha", "zeta", "k1", "broken");
        assertThat(nodes.get(0).group()).isEqualTo(Group.PRIVATE_KEYS);
        assertThat(nodes.get(1).group()).isEqualTo(Group.TRUSTED_CERTIFICATES);
        assertThat(nodes.get(2).group()).isEqualTo(Group.TRUSTED_CERTIFICATES);
        assertThat(nodes.get(3).group()).isEqualTo(Group.SECRET_KEYS);
        assertThat(nodes.get(4).group()).isEqualTo(Group.UNREADABLE_ENTRIES);
    }

    @Test
    @DisplayName("onSelectAlias switches the selection; unknown alias is a no-op")
    void selectAlias() throws Exception {
        X509Certificate cert = leafCert();
        KeyStoreLoadResult result = KeyStoreLoadResult.success(
                KeyStoreContainerType.JKS, "SUN", "17",
                List.of(
                        LoadedEntry.trustedCertificate("a", cert, new Date()),
                        LoadedEntry.trustedCertificate("b", cert, new Date())));
        InspectViewModel vm = new InspectViewModel();
        InspectController c = new InspectController(vm);
        c.onLoadResult(result);

        c.onSelectAlias("b");
        assertThat(vm.getSelectedAlias()).isEqualTo("b");
        assertThat(c.selectedEntry()).isPresent();
        assertThat(c.selectedEntry().get().alias()).isEqualTo("b");

        c.onSelectAlias("does-not-exist");
        assertThat(vm.getSelectedAlias()).isEqualTo("b"); // unchanged

        c.onSelectAlias(null); // no-op
        assertThat(vm.getSelectedAlias()).isEqualTo("b");
    }

    @Test
    @DisplayName("selectedChain returns the entry's certificate chain when present")
    void selectedChainReturnsChain() throws Exception {
        X509Certificate cert = leafCert();
        KeyStoreLoadResult result = KeyStoreLoadResult.success(
                KeyStoreContainerType.JKS, "SUN", "17",
                List.of(LoadedEntry.trustedCertificate("a", cert, new Date())));
        InspectViewModel vm = new InspectViewModel();
        InspectController c = new InspectController(vm);
        c.onLoadResult(result);

        assertThat(c.selectedChain()).isPresent();
        assertThat(c.selectedChain().get()).hasSize(1);
    }

    @Test
    @DisplayName("empty result clears the view-model")
    void emptyResultClearsViewModel() throws Exception {
        InspectViewModel vm = new InspectViewModel();
        InspectController c = new InspectController(vm);
        c.onLoadResult(KeyStoreLoadResult.success(
                KeyStoreContainerType.JKS, "SUN", "17", List.of()));
        assertThat(vm.navNodes()).isEmpty();
        assertThat(vm.getSelectedAlias()).isNull();
        assertThat(c.selectedEntry()).isEmpty();
    }

    @Test
    @DisplayName("applyInspection(null) is a no-op")
    void applyInspectionNullIsNoOp() throws Exception {
        X509Certificate cert = leafCert();
        InspectViewModel vm = new InspectViewModel();
        InspectController c = new InspectController(vm);
        vm.setSelectedAlias("anything");

        c.applyInspection(null);

        assertThat(vm.getInspected()).isNull();
        assertThat(vm.getSelectedAlias()).isEqualTo("anything");
    }

    @Test
    @DisplayName("applyInspection delegates to viewModel.setInspected")
    void applyInspectionDelegates() throws Exception {
        X509Certificate cert = leafCert();
        var inspected = new io.github.certtool.domain.inspect.InspectedKeyStore(
                io.github.certtool.domain.inspect.KeyStoreSummary.from(
                        KeyStoreLoadResult.success(KeyStoreContainerType.JKS, "SUN", "17",
                                List.of(LoadedEntry.trustedCertificate("a", cert, new Date()))),
                        ContentEncoding.BINARY),
                List.of(new io.github.certtool.domain.inspect.InspectedEntry(
                        "a", EntryType.TRUSTED_CERTIFICATE, new Date(), true,
                        null, null, List.of(), List.of())));
        InspectViewModel vm = new InspectViewModel();
        InspectController c = new InspectController(vm);

        c.applyInspection(inspected);

        assertThat(vm.getInspected()).isSameAs(inspected);
    }
}