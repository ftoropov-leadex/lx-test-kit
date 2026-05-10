package io.leadex.aqa.testsupport.retry;

import io.leadex.aqa.config.EnvResolver;
import io.leadex.aqa.testsupport.network.NetworkFailureDetector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.IRetryAnalyzer;
import org.testng.ITestResult;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.locks.LockSupport;
import java.util.stream.Collectors;

public final class FrameworkRetryAnalyzer implements IRetryAnalyzer {

    public static final String RETRY_ATTEMPT_ATTRIBUTE = "framework.retry.attempt";
    public static final String RETRY_REASON_ATTRIBUTE  = "framework.retry.reason";

    private static final Logger LOGGER = LoggerFactory.getLogger(FrameworkRetryAnalyzer.class);

    private static final int         MAX_RETRIES = EnvResolver.integer("FRAMEWORK_RETRY_COUNT", 0);
    private static final long        DELAY_MS    = EnvResolver.integer("FRAMEWORK_RETRY_DELAY_MS", 0);
    private static final Set<String> FILTERS     = parseFilters(EnvResolver.string("FRAMEWORK_RETRY_ON", ""));

    private int failedAttemptCount;

    @Override
    public boolean retry(ITestResult result) {
        if (MAX_RETRIES <= 0) return false;

        Throwable failure = result.getThrowable();
        if (!shouldRetry(failure)) {
            result.setAttribute(RETRY_REASON_ATTRIBUTE, "non-retryable failure");
            return false;
        }
        if (failedAttemptCount >= MAX_RETRIES) {
            result.setAttribute(RETRY_REASON_ATTRIBUTE, "retry limit reached");
            return false;
        }

        failedAttemptCount++;
        result.setAttribute(RETRY_ATTEMPT_ATTRIBUTE, failedAttemptCount);
        result.setAttribute(RETRY_REASON_ATTRIBUTE,
            failure == null ? "unknown" : failure.getClass().getSimpleName());

        LOGGER.warn("Retrying test {}. Attempt {} of {}",
            result.getName(), failedAttemptCount, MAX_RETRIES);

        if (DELAY_MS > 0) {
            LockSupport.parkNanos(DELAY_MS * 1_000_000L);
        }
        return true;
    }

    private boolean shouldRetry(Throwable failure) {
        if (failure == null) return false;
        if (failure instanceof AssertionError) return false;
        if (FILTERS.isEmpty()) return true;

        if (FILTERS.contains("network") && NetworkFailureDetector.hasNetworkCause(failure)) return true;

        String msg = failure.getMessage() == null ? "" : failure.getMessage().toLowerCase(Locale.ROOT);
        if (FILTERS.contains("timeout") && (msg.contains("timed out") || msg.contains("timeout"))) return true;
        if (FILTERS.contains("5xx")     && msg.contains("http 5")) return true;

        return false;
    }

    private static Set<String> parseFilters(String raw) {
        if (raw == null || raw.isBlank()) return Set.of();
        return Arrays.stream(raw.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .map(s -> s.toLowerCase(Locale.ROOT))
            .collect(Collectors.toUnmodifiableSet());
    }
}
