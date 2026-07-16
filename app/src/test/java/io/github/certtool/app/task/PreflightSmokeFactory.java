package io.github.certtool.app.task;

import io.github.certtool.conversion.domain.plan.AliasConflictPolicy;
import io.github.certtool.conversion.domain.plan.ConversionPlan;
import io.github.certtool.conversion.domain.plan.OverwritePolicy;
import io.github.certtool.domain.keystore.ContentEncoding;
import io.github.certtool.domain.keystore.KeyStoreContainerType;
import java.util.List;

final class PreflightSmokeFactory {
    static ConversionPlan nonTrivialPlan() {
        return new ConversionPlan(
                KeyStoreContainerType.JKS, ContentEncoding.BINARY,
                KeyStoreContainerType.JKS, ContentEncoding.BINARY,
                "/tmp/src.jks", "/tmp/tgt.jks",
                "s".toCharArray(), "t".toCharArray(),
                AliasConflictPolicy.RENAME, OverwritePolicy.FAIL_IF_EXISTS,
                List.of("a"), List.of(new char[0]));
    }
}