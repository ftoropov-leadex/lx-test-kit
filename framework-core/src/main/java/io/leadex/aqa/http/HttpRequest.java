package io.leadex.aqa.http;

import io.leadex.aqa.config.HttpVerb;

import java.util.Map;

/**
 * Everything needed to transmit one HTTP call. The framework is a faithful conduit
 * to REST Assured: whatever the caller collected is passed to the wire verbatim —
 * no value guards, no silent drops. Future request facets become a record component,
 * not new {@link HttpClient} overloads.
 *
 * @param method      the verb from the endpoint definition
 * @param url         fully resolved URL (path params already substituted)
 * @param queryParams query parameters; empty when none were set
 * @param headers     per-call headers; empty when none were set. On a name collision
 *                    with a base-spec header the per-call value overwrites
 *                    ({@code X-Correlation-Id} excepted — framework-owned, set last
 *                    by {@link CorrelationIdFilter})
 * @param body        request body, or {@code null} for a bodiless request; transmittable
 *                    on every verb, including GET and DELETE
 */
public record HttpRequest(HttpVerb method, String url, Map<String, ?> queryParams,
                          Map<String, String> headers, Object body) {
}
