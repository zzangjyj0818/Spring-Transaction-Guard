package io.github.zzangjyj0818.transactionguard.core.policy;

import io.github.zzangjyj0818.transactionguard.core.model.JdbcQueryObservation;
import io.github.zzangjyj0818.transactionguard.core.model.TransactionEntryPoint;
import io.github.zzangjyj0818.transactionguard.core.model.TransactionOutcome;
import io.github.zzangjyj0818.transactionguard.core.model.TransactionSnapshot;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QueryBudgetPolicyTest {
    @Test
    void reportsOnlyWhenCountExceedsBudget() {
        QueryBudgetPolicy policy = new QueryBudgetPolicy(2);
        assertEquals(0, policy.evaluate(snapshot(2)).size());
        assertEquals("TG008", policy.evaluate(snapshot(3)).getFirst().code());
        assertEquals(3L, policy.evaluate(snapshot(3)).getFirst().attributes().get("queryCount"));
    }

    @Test
    void supportsZeroBudgetAndRejectsNegativeBudget() {
        assertEquals(1, new QueryBudgetPolicy(0).evaluate(snapshot(1)).size());
        assertThrows(IllegalArgumentException.class, () -> new QueryBudgetPolicy(-1));
    }

    private static TransactionSnapshot snapshot(long queryCount) {
        return new TransactionSnapshot(
                "tx", new TransactionEntryPoint("Service", "run", "Service.run()"),
                Duration.ZERO, TransactionOutcome.COMMITTED, List.of(), List.of(), List.of(),
                new JdbcQueryObservation(queryCount, 0, queryCount));
    }
}
