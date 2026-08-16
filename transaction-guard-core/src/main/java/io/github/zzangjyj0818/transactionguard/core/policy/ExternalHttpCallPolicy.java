package io.github.zzangjyj0818.transactionguard.core.policy;

import io.github.zzangjyj0818.transactionguard.core.model.ExternalCallObservation;
import io.github.zzangjyj0818.transactionguard.core.model.TransactionGuardViolation;
import io.github.zzangjyj0818.transactionguard.core.model.TransactionSnapshot;
import io.github.zzangjyj0818.transactionguard.core.model.ViolationType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

/** Produces one TG002 violation for every external HTTP call in a transaction. */
public final class ExternalHttpCallPolicy implements TransactionGuardPolicy {

    private final Predicate<ExternalCallObservation> violationCandidate;

    /** Creates an external HTTP call policy. */
    public ExternalHttpCallPolicy() {
        this(call -> true);
    }

    /** Creates a policy that evaluates only calls accepted by the predicate. */
    public ExternalHttpCallPolicy(Predicate<ExternalCallObservation> violationCandidate) {
        this.violationCandidate = Objects.requireNonNull(
                violationCandidate, "violationCandidate must not be null");
    }

    @Override
    public List<TransactionGuardViolation> evaluate(TransactionSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        List<TransactionGuardViolation> violations = new ArrayList<>();
        for (ExternalCallObservation call : snapshot.externalCalls()) {
            if (!violationCandidate.test(call)) {
                continue;
            }
            violations.add(PolicyViolations.warn(
                    ViolationType.EXTERNAL_HTTP_CALL_IN_TRANSACTION,
                    "External HTTP call occurred while a database transaction was active",
                    snapshot,
                    attributes(call)
            ));
        }
        return List.copyOf(violations);
    }

    static Map<String, Object> attributes(ExternalCallObservation call) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("clientType", call.clientType().name());
        attributes.put("httpMethod", call.httpMethod());
        attributes.put("host", call.host());
        attributes.put("path", call.path());
        attributes.put("durationNanos", call.durationNanos());
        attributes.put("outcome", call.outcome().name());
        if (call.exceptionType() != null) {
            attributes.put("exceptionType", call.exceptionType());
        }
        return Map.copyOf(attributes);
    }
}
