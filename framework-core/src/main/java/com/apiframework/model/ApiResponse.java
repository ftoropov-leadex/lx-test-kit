package com.apiframework.model;

import java.util.Map;

public record ApiResponse<T>(
    int statusCode,
    Map<String, String> headers,
    T body,
    long durationMs,
    String correlationId,
    String rawBody
) {
}
