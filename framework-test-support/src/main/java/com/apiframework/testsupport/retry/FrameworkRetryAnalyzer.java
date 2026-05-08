package com.apiframework.testsupport.retry;

import com.apiframework.config.EnvResolver;
import com.apiframework.testsupport.network.NetworkAwareMethodListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.IRetryAnalyzer;
import org.testng.ITestResult;

import java.lang.reflect.Method;
import java.util.concurrent.locks.LockSupport;

public final class FrameworkRetryAnalyzer implements IRetryAnalyzer {
    public static final String RETRY_ATTEMPT_ATTRIBUTE = "framework.retry.attempt";
    public static final String RETRIED_ATTRIBUTE = "framework.retry.retried";
    public static final String RETRY_REASON_ATTRIBUTE = "framework.retry.reason";

    private static final Logger LOGGER = LoggerFactory.getLogger(FrameworkRetryAnalyzer.class);

    private int failedAttemptCount;

    @Override
    public boolean retry(ITestResult result) {
        RetryRuntimePolicy policy = resolvePolicy(result);
        Throwable failure = result.getThrowable();

        if (!shouldRetry(failure)) {
            result.setAttribute(RETRIED_ATTRIBUTE, false);
            result.setAttribute(RETRY_REASON_ATTRIBUTE, "non-retryable failure");
            return false;
        }

        if (failedAttemptCount >= policy.maxRetries()) {
            LOGGER.error("Retry limit reached for test {}. Retries: {}", result.getName(), policy.maxRetries());
            result.setAttribute(RETRIED_ATTRIBUTE, false);
            result.setAttribute(RETRY_REASON_ATTRIBUTE, "retry limit reached");
            return false;
        }

        failedAttemptCount++;
        int nextRunNumber = failedAttemptCount + 1;
        result.setAttribute(RETRY_ATTEMPT_ATTRIBUTE, failedAttemptCount);
        result.setAttribute(RETRIED_ATTRIBUTE, true);
        result.setAttribute(RETRY_REASON_ATTRIBUTE, failure == null ? "unknown" : failure.getClass().getSimpleName());

        LOGGER.warn("Retrying test {}. Attempt {} of {}", result.getName(), nextRunNumber, policy.maxAttempts());

        long delayMs = policy.baseDelayMs();
        if (delayMs > 0) {
            LockSupport.parkNanos(delayMs * 1_000_000L);
        }
        return true;
    }

    // Inlined from DefaultRetryPredicate
    private boolean shouldRetry(Throwable failure) {
        if (failure == null) return false;
        if (failure instanceof AssertionError) return false;
        if (NetworkAwareMethodListener.hasNetworkCause(failure)) return true;
        String message = failure.getMessage() == null ? "" : failure.getMessage().toLowerCase();
        return message.contains("connection reset")
            || message.contains("read timed out")
            || message.contains("gateway timeout")
            || message.contains("http 502")
            || message.contains("http 503")
            || message.contains("http 504");
    }

    // Inlined from RetryConfiguration.globalPolicy()
    private RetryRuntimePolicy resolvePolicy(ITestResult result) {
        int maxRetries  = EnvResolver.integer("FRAMEWORK_RETRY_MAX_RETRIES", 1);
        long delayMs    = Long.parseLong(EnvResolver.string("FRAMEWORK_RETRY_DELAY_MS", "500"));
        RetryRuntimePolicy global = new RetryRuntimePolicy(maxRetries, delayMs);

        Method method = result.getMethod().getConstructorOrMethod().getMethod();
        if (method != null) {
            RetrySetting methodPolicy = method.getAnnotation(RetrySetting.class);
            if (methodPolicy != null) return merge(global, methodPolicy);
        }

        RetrySetting classPolicy = result.getTestClass().getRealClass().getAnnotation(RetrySetting.class);
        if (classPolicy != null) return merge(global, classPolicy);

        return global;
    }

    private RetryRuntimePolicy merge(RetryRuntimePolicy global, RetrySetting override) {
        int maxRetries = override.maxRetries() >= 0 ? override.maxRetries() : global.maxRetries();
        long delay = override.delayMs() >= 0 ? override.delayMs() : global.baseDelayMs();
        return new RetryRuntimePolicy(maxRetries, delay);
    }
}
