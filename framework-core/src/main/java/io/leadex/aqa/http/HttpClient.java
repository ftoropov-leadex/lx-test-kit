package io.leadex.aqa.http;

import io.leadex.aqa.model.ApiResponse;

/**
 * Fluent HTTP client interface. Implemented by {@link RestAssuredHttpClient}.
 * This interface defines the API surface for domain modules — it is not an abstraction
 * boundary for swapping HTTP libraries. REST Assured is a core framework dependency.
 */
public interface HttpClient {

    <T> ApiResponse<T> send(HttpRequest request, Class<T> responseType);
}
