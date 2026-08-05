package io.leadex.aqa.testsupport.client;

import io.leadex.aqa.config.EndpointDefinition;
import io.leadex.aqa.config.UrlResolver;
import io.leadex.aqa.http.HttpClient;
import io.leadex.aqa.model.ApiResponse;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Fluent builder for a single API request.
 *
 * <p>Explicit-null rule — no hidden transformations, what you pass is what goes on the
 * wire: {@code null} passed to {@link #query}, {@link #header}, or {@link #pathParam}
 * is sent as the literal string {@code "null"}; {@code null} passed to
 * {@link #bodyField} is written as JSON {@code null}. Empty string is sent empty.
 * The only way to omit a parameter is to not call the setter. This enables negative
 * testing — deliberately sending {@code "null"}, empty, or arbitrary values.
 *
 * <p>Headers guard: {@link #header} populates the map but {@link #send()} throws
 * {@link UnsupportedOperationException} if the map is non-empty — per-call header wiring
 * through {@link HttpClient} is deferred to the HttpRequest record follow-up.
 */
public final class ApiRequestBuilder<T> {

    private final HttpClient           client;
    private final String               baseUrl;
    private final EndpointDefinition   endpoint;
    private final Class<T>             responseType;
    private final Map<String, Object>  query      = new LinkedHashMap<>();
    private final Map<String, String>  headers    = new LinkedHashMap<>();
    private final Map<String, Object>  pathParams = new LinkedHashMap<>();
    private Map<String, Object>        bodyFields;

    /** Framework-internal: intended to be instantiated only by {@code BaseApiTest.call(...)}. */
    public ApiRequestBuilder(HttpClient client, String baseUrl,
                             EndpointDefinition endpoint, Class<T> responseType) {
        this.client       = client;
        this.baseUrl      = baseUrl;
        this.endpoint     = endpoint;
        this.responseType = responseType;
    }

    /** Adds a query parameter. A {@code null} value is sent as the literal string {@code "null"}. */
    public ApiRequestBuilder<T> query(String k, Object v) {
        query.put(k, v == null ? "null" : v);
        return this;
    }

    /**
     * Adds a request header. A {@code null} value is sent as the literal string {@code "null"}.
     * Note: header wiring is not yet supported — {@link #send()} throws if the map is non-empty.
     */
    public ApiRequestBuilder<T> header(String k, String v) {
        headers.put(k, v == null ? "null" : v);
        return this;
    }

    /**
     * Adds a path parameter for {@code {key}} substitution in relUrl.
     * A {@code null} value is sent as the literal string {@code "null"}.
     */
    public ApiRequestBuilder<T> pathParam(String k, Object v) {
        pathParams.put(k, v == null ? "null" : v);
        return this;
    }

    /** Adds a single field to the JSON request body. A {@code null} value is written as JSON {@code null}. */
    public ApiRequestBuilder<T> bodyField(String k, Object v) {
        if (bodyFields == null) bodyFields = new LinkedHashMap<>();
        bodyFields.put(k, v);
        return this;
    }

    /** Executes the request and returns the response. */
    public ApiResponse<T> send() {
        if (!headers.isEmpty()) {
            throw new UnsupportedOperationException(
                "Per-call headers not yet supported. See follow-up: HttpRequest record refactor.");
        }
        String resolvedUrl = UrlResolver.resolve(baseUrl, endpoint.relUrl(), pathParams);
        return switch (endpoint.method()) {
            case GET    -> client.get(resolvedUrl, query, responseType);
            case POST   -> client.post(resolvedUrl, bodyFields, responseType);
            case PUT    -> client.put(resolvedUrl, bodyFields, responseType);
            case PATCH  -> client.patch(resolvedUrl, bodyFields, responseType);
            case DELETE -> client.delete(resolvedUrl, responseType);
        };
    }
}
