package io.github.zzangjyj0818.transactionguard.core.model;

/** Supported external client instrumentation types. */
public enum ExternalClientType {
    /** Spring Framework blocking {@code RestClient}. */
    REST_CLIENT,
    /** OpenFeign blocking client. */
    OPEN_FEIGN
}
