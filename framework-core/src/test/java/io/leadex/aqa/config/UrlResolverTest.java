package io.leadex.aqa.config;

import org.testng.annotations.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the URL-composition contract: literal {@code {key}} substitution (incl. non-String
 * values via {@code String.valueOf}), baseUrl + relUrl concatenation, the null/empty-baseUrl
 * degrade-to-path guard, and the conduit rule — a {@code null} param value transmits the
 * literal string {@code "null"}, a missing param key stays a literal.
 */
public class UrlResolverTest {

    @Test
    public void substitutesAllPathParams() {
        Map<String, Object> params = Map.of("userId", 42, "orderId", "ord-7");
        String url = UrlResolver.resolve("https://api.example",
                "/v1/{userId}/orders/{orderId}/rel/{userId}", params);
        assertThat(url).isEqualTo("https://api.example/v1/42/orders/ord-7/rel/42");
    }

    @Test
    public void concatenatesBaseUrlWithRelUrl() {
        assertThat(UrlResolver.resolve("https://api.example", "/v1/users", Map.of()))
                .isEqualTo("https://api.example/v1/users");
    }

    @Test
    public void nullOrEmptyBaseUrlYieldsResolvedPathOnly() {
        assertThat(UrlResolver.resolve(null, "/v1/users", Map.of())).isEqualTo("/v1/users");
        assertThat(UrlResolver.resolve("", "/v1/users", Map.of())).isEqualTo("/v1/users");
    }

    @Test
    public void missingParamKeyStaysLiteralAndNullValueBecomesStringNull() {
        assertThat(UrlResolver.resolve("https://api.example", "/v1/{id}", Map.of("other", "x")))
                .isEqualTo("https://api.example/v1/{id}");

        Map<String, Object> nullParam = new LinkedHashMap<>();
        nullParam.put("id", null);
        assertThat(UrlResolver.resolve("https://api.example", "/v1/{id}", nullParam))
                .isEqualTo("https://api.example/v1/null");
    }

    @Test
    public void paramValueWithRegexMetacharactersStaysLiteral() {
        assertThat(UrlResolver.resolve("https://api.example", "/v1/{code}",
                Collections.singletonMap("code", "a$5\\b")))
                .isEqualTo("https://api.example/v1/a$5\\b");
    }
}
