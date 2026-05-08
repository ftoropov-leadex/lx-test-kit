package com.apiframework.testsupport.retry;

public record RetryRuntimePolicy(int maxRetries, long baseDelayMs) {
    public RetryRuntimePolicy {
        if (maxRetries < 0) {
            throw new IllegalArgumentException("maxRetries must be >= 0");
        }
        if (baseDelayMs < 0) {
            throw new IllegalArgumentException("baseDelayMs must be >= 0");
        }
    }

    public int maxAttempts() {
        return maxRetries + 1;
    }
}
