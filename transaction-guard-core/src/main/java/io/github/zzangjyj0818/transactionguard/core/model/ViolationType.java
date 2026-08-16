package io.github.zzangjyj0818.transactionguard.core.model;

/** Types of unsafe transaction usage detected by Transaction Guard. */
public enum ViolationType {
    /** Transaction duration exceeded its configured limit. */
    LONG_TRANSACTION("TG001"),
    /** External HTTP call occurred while a transaction was active. */
    EXTERNAL_HTTP_CALL_IN_TRANSACTION("TG002"),
    /** External HTTP call duration exceeded its configured limit. */
    SLOW_EXTERNAL_HTTP_CALL_IN_TRANSACTION("TG003");

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
