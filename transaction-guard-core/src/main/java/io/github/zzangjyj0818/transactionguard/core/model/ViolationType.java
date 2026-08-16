package io.github.zzangjyj0818.transactionguard.core.model;

/** Types of unsafe transaction usage detected by Transaction Guard. */
public enum ViolationType {
    /** Transaction duration exceeded its configured limit. */
    LONG_TRANSACTION("TG001"),
    /** External HTTP call occurred while a transaction was active. */
    EXTERNAL_HTTP_CALL_IN_TRANSACTION("TG002"),
    /** External HTTP call duration exceeded its configured limit. */
    SLOW_EXTERNAL_HTTP_CALL_IN_TRANSACTION("TG003"),
    /** Redis operation occurred while a transaction was active. */
    REDIS_OPERATION_IN_TRANSACTION("TG004"),
    /** Redis operation duration exceeded its configured limit. */
    SLOW_REDIS_OPERATION_IN_TRANSACTION("TG005"),
    /** Kafka producer call occurred while a transaction was active. */
    KAFKA_PRODUCER_CALL_IN_TRANSACTION("TG006"),
    /** Kafka producer call duration exceeded its configured limit. */
    SLOW_KAFKA_PRODUCER_CALL_IN_TRANSACTION("TG007");

    private final String code;

    ViolationType(String code) {
        this.code = code;
    }

    /**
     * Returns the stable public violation code.
     *
     * @return violation code such as {@code TG001}
     */
    public String code() {
        return code;
    }
}
