package io.github.zzangjyj0818.transactionguard.spring.http;

import feign.Client;
import feign.Request;
import feign.Response;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TransactionGuardFeignCapabilityTest {

    @Test
    void decoratesClientOnlyOnce() {
        TransactionGuardFeignCapability capability = new TransactionGuardFeignCapability(
                new TransactionGuardHttpRecorder(
                        new io.github.zzangjyj0818.transactionguard.spring.transaction.TransactionGuardContextRegistry()));
        Client delegate = (request, options) -> response(request, 200);

        Client decorated = capability.enrich(delegate);

        assertInstanceOf(TransactionGuardFeignClient.class, decorated);
        assertSame(decorated, capability.enrich(decorated));
    }

    @Test
    void preservesOriginalFeignTransportFailure() {
        IOException expected = new IOException("transport failed");
        Client delegate = (request, options) -> {
            throw expected;
        };
        TransactionGuardFeignClient client = new TransactionGuardFeignClient(
                delegate,
                new TransactionGuardHttpRecorder(
                        new io.github.zzangjyj0818.transactionguard.spring.transaction.TransactionGuardContextRegistry()));

        IOException actual = assertThrows(IOException.class,
                () -> client.execute(request(), new Request.Options()));

        assertSame(expected, actual);
    }

    private static Request request() {
        return Request.create(
                Request.HttpMethod.GET,
                "https://payments.example.com/payments?token=secret",
                Map.of(), null, StandardCharsets.UTF_8, null);
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
