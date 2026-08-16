package io.github.zzangjyj0818.transactionguard.core.policy;

import io.github.zzangjyj0818.transactionguard.core.model.TransactionGuardViolation;
import io.github.zzangjyj0818.transactionguard.core.model.TransactionSnapshot;
import io.github.zzangjyj0818.transactionguard.core.model.ViolationSeverity;
import io.github.zzangjyj0818.transactionguard.core.model.ViolationType;

import java.util.Map;

final class PolicyViolations {

    private PolicyViolations() {
    }

    static TransactionGuardViolation warn(
            ViolationType type,
            String message,
            TransactionSnapshot snapshot,
            Map<String, Object> attributes
    ) {
        return new TransactionGuardViolation(
                type.code(), type, ViolationSeverity.WARN, message, snapshot, attributes);
    }
}
