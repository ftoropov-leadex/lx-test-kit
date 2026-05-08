package com.apiframework.config;

public record EndpointDefinition(HttpVerb method, String relUrl) {
    public EndpointDefinition {
        if (method == null)              throw new IllegalArgumentException("method must not be null");
        if (relUrl == null || relUrl.isBlank()) throw new IllegalArgumentException("relUrl must not be blank");
    }
}
