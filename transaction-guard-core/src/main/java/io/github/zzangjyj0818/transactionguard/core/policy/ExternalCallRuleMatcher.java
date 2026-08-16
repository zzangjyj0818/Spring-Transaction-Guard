package io.github.zzangjyj0818.transactionguard.core.policy;

import io.github.zzangjyj0818.transactionguard.core.model.ExternalCallObservation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/** Matches sanitized external-call destinations against configured glob rules. */
public final class ExternalCallRuleMatcher {

    private final List<Pattern> ignoreHosts;
    private final List<Pattern> ignoreEndpoints;
    private final List<Pattern> allowHosts;
    private final List<Pattern> allowEndpoints;

    /**
     * Creates a matcher. Host globs are case-insensitive and endpoint globs use {@code host/path}.
     */
    public ExternalCallRuleMatcher(
            List<String> ignoreHosts,
            List<String> ignoreEndpoints,
            List<String> allowHosts,
            List<String> allowEndpoints
    ) {
        this.ignoreHosts = compile(ignoreHosts, true);
        this.ignoreEndpoints = compile(ignoreEndpoints, false);
        this.allowHosts = compile(allowHosts, true);
        this.allowEndpoints = compile(allowEndpoints, false);
    }

    /** Creates a matcher with no configured rules. */
    public static ExternalCallRuleMatcher none() {
        return new ExternalCallRuleMatcher(List.of(), List.of(), List.of(), List.of());
    }

    /** Returns whether the call must not be added to a transaction snapshot. */
    public boolean isIgnored(ExternalCallObservation call) {
        Objects.requireNonNull(call, "call must not be null");
        return matches(ignoreHosts, normalizedHost(call.host()))
                || matches(ignoreEndpoints, endpoint(call));
    }

    /** Returns whether the observed call must be excluded from TG002 and TG003. */
    public boolean isAllowed(ExternalCallObservation call) {
        Objects.requireNonNull(call, "call must not be null");
        if (isIgnored(call)) {
            return false;
        }
        return matches(allowHosts, normalizedHost(call.host()))
                || matches(allowEndpoints, endpoint(call));
    }

    private static String endpoint(ExternalCallObservation call) {
        String path = call.path().startsWith("/") ? call.path() : "/" + call.path();
        return normalizedHost(call.host()) + path;
    }

    private static String normalizedHost(String host) {
        String normalized = host.trim().toLowerCase(Locale.ROOT);
        while (normalized.endsWith(".")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static List<Pattern> compile(List<String> values, boolean lowerCase) {
        Objects.requireNonNull(values, "rule list must not be null");
        List<Pattern> patterns = new ArrayList<>();
        for (String value : values) {
            Objects.requireNonNull(value, "rule must not be null");
            String normalized = value.trim();
            if (normalized.isEmpty()) {
                throw new IllegalArgumentException("rule must not be blank");
            }
            if (lowerCase) {
                normalized = normalizedHost(normalized);
            } else {
                int slash = normalized.indexOf('/');
                String host = slash < 0 ? normalized : normalized.substring(0, slash);
                String path = slash < 0 ? "/" : normalized.substring(slash);
                normalized = normalizedHost(host) + (path.startsWith("/") ? path : "/" + path);
            }
            patterns.add(Pattern.compile(globRegex(normalized)));
        }
        return List.copyOf(patterns);
    }

    private static String globRegex(String glob) {
        StringBuilder regex = new StringBuilder("^");
        for (int index = 0; index < glob.length(); index++) {
            char character = glob.charAt(index);
            if (character == '*') {
                regex.append(".*");
            } else if (character == '?') {
                regex.append('.');
            } else {
                regex.append(Pattern.quote(String.valueOf(character)));
            }
        }
        return regex.append('$').toString();
    }

    private static boolean matches(List<Pattern> patterns, String value) {
        return patterns.stream().anyMatch(pattern -> pattern.matcher(value).matches());
    }
}
