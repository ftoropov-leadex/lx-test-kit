package io.leadex.aqa.testsupport.client;

import io.leadex.aqa.config.EndpointDefinition;
import io.leadex.aqa.config.UrlResolver;
import io.leadex.aqa.http.HttpClient;
import io.leadex.aqa.http.HttpRequest;
import io.leadex.aqa.model.ApiResponse;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Fluent builder for a single API request.
 *
 * <p>Conduit principle — the framework is a faithful conduit to REST Assured: every
 * setter value goes on the wire verbatim, for every HTTP method. {@link #query},
 * {@link #header}, and {@link #bodyField} are transmittable on GET, POST, PUT, PATCH,
 * and DELETE alike; nothing is silently dropped. The framework adds zero judgment on
 * values — deliberately "invalid" input is the test's business, never the framework's.
 *
 * <p>Explicit-null rule — no hidden transformations, what you pass is what goes on the
 * wire: {@code null} passed to {@link #query}, {@link #header}, or {@link #pathParam}
 * is sent as the literal string {@code "null"}; {@code null} passed to
 * {@link #bodyField} is written as JSON {@code null}. Empty string is sent empty.
 * The only way to omit a parameter is to not call the setter. This enables negative
 * testing — deliberately sending {@code "null"}, empty, or arbitrary values.
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

    /** Adds a request header. A {@code null} value is sent as the literal string {@code "null"}. */
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
        String resolvedUrl = UrlResolver.resolve(baseUrl, endpoint.relUrl(), pathParams);
        return client.send(
            new HttpRequest(endpoint.method(), resolvedUrl, query, headers, bodyFields),
            responseType);
    }
}
