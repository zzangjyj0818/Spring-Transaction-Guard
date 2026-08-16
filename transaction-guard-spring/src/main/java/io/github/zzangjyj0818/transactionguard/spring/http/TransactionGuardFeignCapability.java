package io.github.zzangjyj0818.transactionguard.spring.http;

import feign.Capability;
import feign.Client;

import java.util.Objects;

/** OpenFeign capability that decorates each client with Transaction Guard observation. */
public final class TransactionGuardFeignCapability implements Capability {

    private final TransactionGuardHttpRecorder recorder;

    /** Creates the capability. */
    public TransactionGuardFeignCapability(TransactionGuardHttpRecorder recorder) {
        this.recorder = Objects.requireNonNull(recorder, "recorder must not be null");
    }

    @Override
    public Client enrich(Client client) {
        if (client instanceof TransactionGuardFeignClient) {
            return client;
        }
        return new TransactionGuardFeignClient(client, recorder);
    }
}
