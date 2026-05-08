package com.apiframework.testsupport.client;

import com.apiframework.config.EndpointDefinition;
import com.apiframework.config.UrlResolver;
import com.apiframework.http.HttpClient;
import com.apiframework.model.ApiResponse;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Fluent builder for a single API request. Instantiated only by {@code BaseApiTest.call(...)}.
 *
 * <p>Null-omit rule: {@link #query}, {@link #header}, {@link #pathParam}, and {@link #bodyField}
 * silently skip {@code null} values — the setter is a no-op when {@code v == null}.
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

    /** Adds a query parameter. Null values are silently skipped. */
    public ApiRequestBuilder<T> query(String k, Object v) {
        if (v != null) query.put(k, v);
        return this;
    }

    /**
     * Adds a request header. Null values are silently skipped.
     * Note: header wiring is not yet supported — {@link #send()} throws if the map is non-empty.
     */
    public ApiRequestBuilder<T> header(String k, String v) {
        if (v != null) headers.put(k, v);
        return this;
    }

    /** Adds a path parameter for {@code {key}} substitution in relUrl. Null values are silently skipped. */
    public ApiRequestBuilder<T> pathParam(String k, Object v) {
        if (v != null) pathParams.put(k, v);
        return this;
    }

    /** Adds a single field to the JSON request body. Null values are silently skipped. */
    public ApiRequestBuilder<T> bodyField(String k, Object v) {
        if (bodyFields == null) bodyFields = new LinkedHashMap<>();
        if (v != null) bodyFields.put(k, v);
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
