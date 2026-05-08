package com.apiframework.config;

public record FrameworkRuntimeConfig(
    String env,
    String baseUrl,
    int connectTimeoutMs,
    int readTimeoutMs,
    String clientName,
    String clientSecret
) {
}
