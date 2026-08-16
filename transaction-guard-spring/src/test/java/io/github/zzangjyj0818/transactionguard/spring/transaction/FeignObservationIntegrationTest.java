package io.github.zzangjyj0818.transactionguard.spring.transaction;

import feign.Client;
import feign.Request;
import feign.Response;
import io.github.zzangjyj0818.transactionguard.core.model.ExternalClientType;
import io.github.zzangjyj0818.transactionguard.core.model.TransactionEntryPoint;
import io.github.zzangjyj0818.transactionguard.core.model.TransactionGuardViolation;
import io.github.zzangjyj0818.transactionguard.core.model.TransactionSnapshot;
import io.github.zzangjyj0818.transactionguard.core.policy.TransactionGuardPolicy;
import io.github.zzangjyj0818.transactionguard.core.reporter.TransactionGuardReporter;
import io.github.zzangjyj0818.transactionguard.spring.http.TransactionGuardFeignClient;
import io.github.zzangjyj0818.transactionguard.spring.http.TransactionGuardHttpRecorder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FeignObservationIntegrationTest {

    @AfterEach
    void clearTransactionState() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
        TransactionSynchronizationManager.clear();
    }

    @Test
    void recordsSanitizedFeignCallInsideObservedTransaction() throws IOException {
        List<TransactionSnapshot> snapshots = new ArrayList<>();
        TransactionGuardContextRegistry registry = observeTransaction(snapshots);
        Client delegate = (request, options) -> response(request, 200);
        TransactionGuardFeignClient client = new TransactionGuardFeignClient(
                delegate, new TransactionGuardHttpRecorder(registry));

        client.execute(request(), new Request.Options());
        completeTransaction();

        var call = snapshots.getFirst().externalCalls().getFirst();
        assertEquals(ExternalClientType.OPEN_FEIGN, call.clientType());
        assertEquals("payments.example.com", call.host());
        assertEquals("/payments", call.path());
        assertFalse(call.path().contains("secret"));
    }

    @Test
    void recordsFailureAndRethrowsOriginalException() {
        List<TransactionSnapshot> snapshots = new ArrayList<>();
        TransactionGuardContextRegistry registry = observeTransaction(snapshots);
        IOException expected = new IOException("transport failed");
        Client delegate = (request, options) -> {
            throw expected;
        };
        TransactionGuardFeignClient client = new TransactionGuardFeignClient(
                delegate, new TransactionGuardHttpRecorder(registry));

        IOException actual = assertThrows(IOException.class,
                () -> client.execute(request(), new Request.Options()));
        completeTransaction();

        assertSame(expected, actual);
        assertEquals(IOException.class.getName(),
                snapshots.getFirst().externalCalls().getFirst().exceptionType());
    }

    private static TransactionGuardContextRegistry observeTransaction(List<TransactionSnapshot> snapshots) {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionGuardContextRegistry registry = new TransactionGuardContextRegistry();
        TransactionGuardPolicy capture = snapshot -> {
            snapshots.add(snapshot);
            return List.of();
        };
        TransactionGuardReporter reporter = new TransactionGuardReporter() {
            @Override
            public void report(List<TransactionGuardViolation> violations) {
            }
        };
        TransactionObservation observation = new TransactionObservation(
                new ActualTransactionDetector(), registry, List.of(capture), reporter);
        observation.observe(new TransactionEntryPoint("ExampleService", "call", "ExampleService#call"));
        return registry;
    }

    private static void completeTransaction() {
        List<TransactionSynchronization> synchronizations =
                List.copyOf(TransactionSynchronizationManager.getSynchronizations());
        synchronizations.forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
        TransactionSynchronizationManager.clearSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    private static Request request() {
        return Request.create(
                Request.HttpMethod.GET,
                "https://payments.example.com/payments?token=secret",
                Map.of("Authorization", List.of("Bearer secret")),
                null, StandardCharsets.UTF_8, null);
    }

    private static Response response(Request request, int status) {
        return Response.builder()
                .request(request)
                .status(status)
                .reason("test")
                .headers(Map.of())
                .protocolVersion(Request.ProtocolVersion.HTTP_1_1)
                .build();
    }
}
