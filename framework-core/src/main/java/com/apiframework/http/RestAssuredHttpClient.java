package com.apiframework.http;

import com.apiframework.json.JacksonProvider;
import com.apiframework.model.ApiResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Core HTTP client implementation backed by REST Assured.
 * REST Assured is a non-replaceable foundation of this framework.
 */
public final class RestAssuredHttpClient implements HttpClient {

    private static final Logger log = LoggerFactory.getLogger(RestAssuredHttpClient.class);

    private final RequestSpecification baseSpec;
    private final ObjectMapper objectMapper;

    public RestAssuredHttpClient(RequestSpecification baseSpec) {
        this(baseSpec, JacksonProvider.defaultMapper());
    }

    public RestAssuredHttpClient(RequestSpecification baseSpec, ObjectMapper objectMapper) {
        this.baseSpec = baseSpec;
        this.objectMapper = objectMapper;
    }

    @Override
    public <T> ApiResponse<T> get(String path, Class<T> responseType) {
        return get(path, Map.of(), responseType);
    }

    @Override
    public <T> ApiResponse<T> get(String path, Map<String, ?> queryParams, Class<T> responseType) {
        return execute(path, "GET", null, queryParams, responseType);
    }

    @Override
    public <T> ApiResponse<T> post(String path, Object body, Class<T> responseType) {
        return execute(path, "POST", body, Map.of(), responseType);
    }

    @Override
    public <T> ApiResponse<T> put(String path, Object body, Class<T> responseType) {
        return execute(path, "PUT", body, Map.of(), responseType);
    }

    @Override
    public <T> ApiResponse<T> patch(String path, Object body, Class<T> responseType) {
        return execute(path, "PATCH", body, Map.of(), responseType);
    }

    @Override
    public <T> ApiResponse<T> delete(String path, Class<T> responseType) {
        return execute(path, "DELETE", null, Map.of(), responseType);
    }

    // --- internals ---

    private <T> ApiResponse<T> execute(
        String path, String method, Object body, Map<String, ?> queryParams, Class<T> responseType
    ) {
        Objects.requireNonNull(method, "HTTP method must not be null");
        Objects.requireNonNull(path, "Request path must not be null");

        RequestSpecification spec = RestAssured.given().spec(baseSpec);

        if (queryParams != null && !queryParams.isEmpty()) {
            spec.queryParams(queryParams);
        }

        if (body != null) {
            spec.body(body);
        }

        Response response = switch (method) {
            case "GET" -> spec.get(path);
            case "POST" -> spec.post(path);
            case "PUT" -> spec.put(path);
            case "PATCH" -> spec.patch(path);
            case "DELETE" -> spec.delete(path);
            default -> throw new IllegalArgumentException("Unsupported HTTP method: " + method);
        };

        String rawBody = response.getBody() == null ? "" : response.getBody().asString();
        ApiResponse<T> apiResponse = toApiResponse(response, rawBody, responseType);

        log.info("{} {} → {} ({}ms) [correlationId={}]",
            method, path, apiResponse.statusCode(), apiResponse.durationMs(), apiResponse.correlationId());

        if (log.isDebugEnabled()) {
            if (queryParams != null && !queryParams.isEmpty()) {
                log.debug("  query params: {}", queryParams);
            }
            if (body != null) {
                log.debug("  request body: {}", body);
            }
            log.debug("  response body: {}", rawBody);
        }

        return apiResponse;
    }

    private <T> ApiResponse<T> toApiResponse(Response response, String rawBody, Class<T> responseType) {
        T body = deserializeBody(rawBody, responseType);
        Map<String, String> headers = new LinkedHashMap<>();
        response.getHeaders().asList().forEach(header -> headers.put(header.getName(), header.getValue()));
        String correlationId = headers.get("X-Correlation-Id");

        return new ApiResponse<>(
            response.statusCode(),
            headers,
            body,
            response.time(),
            correlationId,
            rawBody
        );
    }

    private <T> T deserializeBody(String rawBody, Class<T> responseType) {
        if (responseType == String.class) {
            return responseType.cast(rawBody);
        }
        if (rawBody == null || rawBody.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(rawBody, responseType);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to deserialize response body", exception);
        }
    }
}
