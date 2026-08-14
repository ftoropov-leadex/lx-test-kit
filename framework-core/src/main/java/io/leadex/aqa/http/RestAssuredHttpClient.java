package io.leadex.aqa.http;

import io.leadex.aqa.json.JacksonProvider;
import io.leadex.aqa.model.ApiResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.restassured.RestAssured;
import io.restassured.config.HeaderConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.response.Response;
import io.restassured.specification.QueryableRequestSpecification;
import io.restassured.specification.RequestSpecification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
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
    public <T> ApiResponse<T> send(HttpRequest request, Class<T> responseType) {
        Objects.requireNonNull(request, "HttpRequest must not be null");
        Objects.requireNonNull(request.method(), "HTTP method must not be null");
        Objects.requireNonNull(request.url(), "Request URL must not be null");

        RequestSpecification spec = RestAssured.given().spec(baseSpec);

        Map<String, ?> queryParams = request.queryParams();
        if (queryParams != null && !queryParams.isEmpty()) {
            spec.queryParams(queryParams);
        }

        Map<String, String> headers = request.headers();
        if (headers != null && !headers.isEmpty()) {
            // Per-call headers OVERWRITE same-named base-spec headers (RA's default is a
            // comma-merge — a per-call Content-Type would otherwise become two values).
            // overwriteHeadersWithName only takes effect if the HeaderConfig is in place
            // before spec.headers(...) runs; the merged spec config is kept intact except
            // for the swapped HeaderConfig, so timeouts etc. from the base spec survive.
            String[] names = headers.keySet().toArray(new String[0]);
            HeaderConfig overwrite = HeaderConfig.headerConfig()
                .overwriteHeadersWithName(names[0], Arrays.copyOfRange(names, 1, names.length));
            RestAssuredConfig merged = spec instanceof QueryableRequestSpecification queryable
                ? queryable.getConfig()
                : null;
            spec.config(merged == null
                ? RestAssuredConfig.config().headerConfig(overwrite)
                : merged.headerConfig(overwrite));
            spec.headers(headers);
        }

        Object body = request.body();
        if (body != null) {
            spec.body(body);
        }

        Response response = switch (request.method()) {
            case GET    -> spec.get(request.url());
            case POST   -> spec.post(request.url());
            case PUT    -> spec.put(request.url());
            case PATCH  -> spec.patch(request.url());
            case DELETE -> spec.delete(request.url());
        };

        String rawBody = response.getBody() == null ? "" : response.getBody().asString();
        ApiResponse<T> apiResponse = toApiResponse(response, rawBody, responseType);

        log.info("{} {} → {} ({}ms) [correlationId={}]",
            request.method(), request.url(), apiResponse.statusCode(), apiResponse.durationMs(), apiResponse.correlationId());

        if (log.isDebugEnabled()) {
            if (queryParams != null && !queryParams.isEmpty()) {
                log.debug("  query params: {}", queryParams);
            }
            if (headers != null && !headers.isEmpty()) {
                log.debug("  request headers: {}", headers);
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
        String correlationId = headers.getOrDefault("X-Correlation-Id", CorrelationIdFilter.currentId());

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
