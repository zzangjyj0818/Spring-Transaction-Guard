package io.github.zzangjyj0818.transactionguard.core.reporter;

import io.github.zzangjyj0818.transactionguard.core.exception.TransactionGuardViolationException;
import io.github.zzangjyj0818.transactionguard.core.model.TransactionGuardViolation;
import io.github.zzangjyj0818.transactionguard.core.model.ViolationType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static io.github.zzangjyj0818.transactionguard.core.support.TestFixtures.violation;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TransactionGuardReporterTest {

    @Test
    void throwingReporterDoesNothingForEmptyList() {
        assertDoesNotThrow(() -> new ThrowingTransactionGuardReporter().report(List.of()));
    }

    @Test
    void throwingReporterPreservesEveryViolation() {
        List<TransactionGuardViolation> violations = new ArrayList<>(List.of(
                violation(ViolationType.LONG_TRANSACTION),
                violation(ViolationType.EXTERNAL_HTTP_CALL_IN_TRANSACTION)
        ));

        TransactionGuardViolationException exception = assertThrows(
                TransactionGuardViolationException.class,
                () -> new ThrowingTransactionGuardReporter().report(violations)
        );
        violations.clear();

        assertEquals(2, exception.violations().size());
        assertEquals("Transaction Guard detected 2 violation(s): TG001,TG002", exception.getMessage());
        assertThrows(UnsupportedOperationException.class, () -> exception.violations().clear());
    }

    @Test
    void violationExceptionRejectsEmptyList() {
        assertThrows(IllegalArgumentException.class,
                () -> new TransactionGuardViolationException(List.of()));
    }

    @Test
    void loggingReporterAcceptsEmptyList() {
        assertDoesNotThrow(() -> new LoggingTransactionGuardReporter().report(List.of()));
    }
}
