package io.github.zzangjyj0818.transactionguard.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Configuration properties for Spring Transaction Guard. */
@ConfigurationProperties("transaction-guard")
public final class TransactionGuardProperties {

    private boolean enabled = true;
    private final Transaction transaction = new Transaction();
    private final ExternalCall externalCall = new ExternalCall();
    private final Redis redis = new Redis();
    private final Kafka kafka = new Kafka();
    private final Jdbc jdbc = new Jdbc();
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

    /** Returns Redis observation settings. */
    public Redis getRedis() {
        return redis;
    }

    /** Returns Kafka producer observation settings. */
    public Kafka getKafka() { return kafka; }

    /** Returns JDBC observation settings. */
    public Jdbc getJdbc() { return jdbc; }

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
        private List<String> ignoreHosts = List.of();
        private List<String> ignoreEndpoints = List.of();
        private List<String> allowHosts = List.of();
        private List<String> allowEndpoints = List.of();

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

        /** Returns host patterns that must not be recorded. */
        public List<String> getIgnoreHosts() {
            return ignoreHosts;
        }

        /** Sets host patterns that must not be recorded. */
        public void setIgnoreHosts(List<String> ignoreHosts) {
            this.ignoreHosts = requirePatterns(ignoreHosts, "ignoreHosts");
        }

        /** Returns endpoint patterns that must not be recorded. */
        public List<String> getIgnoreEndpoints() {
            return ignoreEndpoints;
        }

        /** Sets endpoint patterns that must not be recorded. */
        public void setIgnoreEndpoints(List<String> ignoreEndpoints) {
            this.ignoreEndpoints = requirePatterns(ignoreEndpoints, "ignoreEndpoints");
        }

        /** Returns host patterns whose calls are observed but allowed. */
        public List<String> getAllowHosts() {
            return allowHosts;
        }

        /** Sets host patterns whose calls are observed but allowed. */
        public void setAllowHosts(List<String> allowHosts) {
            this.allowHosts = requirePatterns(allowHosts, "allowHosts");
        }

        /** Returns endpoint patterns whose calls are observed but allowed. */
        public List<String> getAllowEndpoints() {
            return allowEndpoints;
        }

        /** Sets endpoint patterns whose calls are observed but allowed. */
        public void setAllowEndpoints(List<String> allowEndpoints) {
            this.allowEndpoints = requirePatterns(allowEndpoints, "allowEndpoints");
        }
    }

    /** Imperative Redis observation settings. */
    public static final class Redis {
        private boolean enabled = true;
        private Duration slowThreshold = Duration.ofSeconds(1);

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Duration getSlowThreshold() {
            return slowThreshold;
        }

        public void setSlowThreshold(Duration slowThreshold) {
            this.slowThreshold = requireNonNegative(slowThreshold, "slowThreshold");
        }
    }

    /** Kafka producer observation settings. */
    public static final class Kafka {
        private boolean enabled = true;
        private Duration slowThreshold = Duration.ofSeconds(1);

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public Duration getSlowThreshold() { return slowThreshold; }
        public void setSlowThreshold(Duration value) {
            slowThreshold = requireNonNegative(value, "slowThreshold");
        }
    }

    /** JDBC query observation settings. */
    public static final class Jdbc {
        private boolean enabled = true;
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }

    /** Violation reporting settings. */
    public static final class Violation {
        private Mode mode = Mode.LOG;
        private Set<ViolationCode> disabledCodes = Set.of();

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

        /** Returns stable violation codes disabled by configuration. */
        public Set<ViolationCode> getDisabledCodes() {
            return disabledCodes;
        }

        /** Sets stable violation codes disabled by configuration. */
        public void setDisabledCodes(Set<ViolationCode> disabledCodes) {
            this.disabledCodes = Set.copyOf(Objects.requireNonNull(
                    disabledCodes, "disabledCodes must not be null"));
        }
    }

    /** Stable violation codes accepted by configuration binding. */
    public enum ViolationCode {
        /** Long transaction. */
        TG001,
        /** External HTTP call inside a transaction. */
        TG002,
        /** Slow external HTTP call inside a transaction. */
        TG003,
        /** Redis operation inside a transaction. */
        TG004,
        /** Slow Redis operation inside a transaction. */
        TG005,
        /** Kafka producer call inside a transaction. */
        TG006,
        /** Slow Kafka producer call inside a transaction. */
        TG007
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

    private static List<String> requirePatterns(List<String> values, String name) {
        Objects.requireNonNull(values, name + " must not be null");
        return values.stream()
                .map(value -> requirePattern(value, name))
                .toList();
    }

    private static String requirePattern(String value, String name) {
        Objects.requireNonNull(value, name + " must not contain null");
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(name + " must not contain blank patterns");
        }
        return trimmed;
    }
}
