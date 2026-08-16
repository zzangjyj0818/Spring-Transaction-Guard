package io.github.zzangjyj0818.transactionguard.spring.http;

import java.net.URI;
import java.util.Objects;

/** Extracts the non-sensitive destination fields retained for an HTTP observation. */
final class ExternalCallUriSanitizer {

    SanitizedDestination sanitize(URI uri) {
        Objects.requireNonNull(uri, "uri must not be null");
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            host = "unknown";
        }
        String path = uri.getRawPath();
        if (path == null || path.isBlank()) {
            path = "/";
        }
        return new SanitizedDestination(host, path);
    }

    record SanitizedDestination(String host, String path) {
    }
}
