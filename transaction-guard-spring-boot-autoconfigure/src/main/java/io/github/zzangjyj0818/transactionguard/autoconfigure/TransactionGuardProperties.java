package io.github.zzangjyj0818.transactionguard.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Objects;

/** Configuration properties for Spring Transaction Guard. */
@ConfigurationProperties("transaction-guard")
public final class TransactionGuardProperties {

    private boolean enabled = true;
    private final Transaction transaction = new Transaction();
    private final ExternalCall externalCall = new ExternalCall();
    private final Violation violation = new Violation();

    /** Creates properties initialized with the documented defaults. */
    public TransactionGuardProperties() {
    }

    /**
     * Returns whether Transaction Guard is enabled.
     *
     * @return whether Transaction Guard is enabled
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Sets whether Transaction Guard is enabled.
     *
     * @param enabled whether Transaction Guard is enabled
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Returns transaction duration settings.
     *
     * @return transaction duration settings
     */
    public Transaction getTransaction() {
        return transaction;
    }

    /**
     * Returns external HTTP call settings.
     *
     * @return external HTTP call settings
     */
    public ExternalCall getExternalCall() {
        return externalCall;
    }

    /**
     * Returns violation reporting settings.
     *
     * @return violation reporting settings
     */
    public Violation getViolation() {
        return violation;
    }

    /** Transaction observation settings. */
    public static final class Transaction {
        private Duration maxDuration = Duration.ofSeconds(2);

        /** Creates transaction settings with the documented defaults. */
        public Transaction() {
        }

        /**
         * Returns the TG001 duration threshold.
         *
         * @return TG001 duration threshold
         */
        public Duration getMaxDuration() {
            return maxDuration;
        }

        /**
         * Sets the TG001 duration threshold.
         *
         * @param maxDuration non-negative TG001 duration threshold
         */
        public void setMaxDuration(Duration maxDuration) {
            this.maxDuration = requireNonNegative(maxDuration, "maxDuration");
        }
    }

    /** External HTTP call observation settings. */
    public static final class ExternalCall {
        private boolean enabled = true;
        private Duration slowThreshold = Duration.ofSeconds(1);

        /** Creates external call settings with the documented defaults. */
        public ExternalCall() {
        }

        /**
         * Returns whether RestClient instrumentation is enabled.
         *
         * @return whether RestClient instrumentation is enabled
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * Sets whether RestClient instrumentation is enabled.
         *
         * @param enabled whether RestClient instrumentation is enabled
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /**
         * Returns the TG003 duration threshold.
         *
         * @return TG003 duration threshold
         */
        public Duration getSlowThreshold() {
            return slowThreshold;
        }

        /**
         * Sets the TG003 duration threshold.
         *
         * @param slowThreshold non-negative TG003 duration threshold
         */
        public void setSlowThreshold(Duration slowThreshold) {
            this.slowThreshold = requireNonNegative(slowThreshold, "slowThreshold");
        }
    }

    /** Violation reporting settings. */
    public static final class Violation {
        private Mode mode = Mode.LOG;

        /** Creates violation settings with the documented defaults. */
        public Violation() {
        }

        /**
         * Returns the configured reporting mode.
         *
         * @return configured reporting mode
         */
        public Mode getMode() {
            return mode;
        }

        /**
         * Sets the configured reporting mode.
         *
         * @param mode reporting mode
         */
        public void setMode(Mode mode) {
            this.mode = Objects.requireNonNull(mode, "mode must not be null");
        }
    }

    /** Supported violation reporting modes. */
    public enum Mode {
        /** Log violations without intentionally interrupting business execution. */
        LOG,
        /** Throw a violation exception for test and CI use. */
        THROW
    }

    private static Duration requireNonNegative(Duration value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isNegative()) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
        return value;
    }
}
