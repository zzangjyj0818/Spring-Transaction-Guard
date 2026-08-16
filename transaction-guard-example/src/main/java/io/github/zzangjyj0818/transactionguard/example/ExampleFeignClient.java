package io.github.zzangjyj0818.transactionguard.example;

import feign.RequestLine;

/** Minimal OpenFeign client used by the runnable example. */
public interface ExampleFeignClient {

    /** Calls the local fast downstream stub. */
    @RequestLine("GET /remote/fast")
    String fast();
}
