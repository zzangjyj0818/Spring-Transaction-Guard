package io.github.zzangjyj0818.transactionguard.core.reporter;

import io.github.zzangjyj0818.transactionguard.core.model.TransactionGuardViolation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;

/** Default reporter that emits one structured WARN log for every violation. */
public final class LoggingTransactionGuardReporter implements TransactionGuardReporter {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoggingTransactionGuardReporter.class);

    /** Creates a logging reporter using the class logger. */
    public LoggingTransactionGuardReporter() {
    }

    @Override
    public void report(List<TransactionGuardViolation> violations) {
        Objects.requireNonNull(violations, "violations must not be null");
        for (TransactionGuardViolation violation : violations) {
            Objects.requireNonNull(violation, "violations must not contain null");
            LOGGER.warn(
                    "transaction_guard_violation code={} type={} severity={} transaction.id={} "
                            + "transaction.duration={} transaction.outcome={} entry.point={} message={}",
                    violation.code(),
                    violation.type(),
                    violation.severity(),
                    violation.transaction().transactionId(),
                    violation.transaction().duration(),
                    violation.transaction().outcome(),
                    violation.transaction().entryPoint().signature(),
                    violation.message()
            );
        }
    }
}
