package com.apiframework.config;

import java.util.Map;

/**
 * Centralized URL composition utility.
 * Owns three concerns: path-param substitution, absolute-URL detection, and baseUrl + relUrl concatenation.
 */
public final class UrlResolver {

    private UrlResolver() {}

    /**
     * Compose the final request URL from environment baseUrl, endpoint relUrl, and optional path parameters.
     *
     * <p>Rules:
     * <ol>
     *   <li>Substitute {@code {key}} placeholders in relUrl with pathParams values.</li>
     *   <li>If the resulting path is already absolute (starts with {@code http://} or {@code https://}),
     *       return it as-is — this is the dev kostyl for public APIs where baseUrl is empty.</li>
     *   <li>Otherwise concatenate baseUrl + resolved path.</li>
     * </ol>
     */
    public static String resolve(String baseUrl, String relUrl, Map<String, Object> pathParams) {
        String resolved = applyPathParams(relUrl, pathParams);
        if (resolved.startsWith("http://") || resolved.startsWith("https://")) {
            return resolved;
        }
        return (baseUrl != null ? baseUrl : "") + resolved;
    }

    private static String applyPathParams(String tpl, Map<String, Object> params) {
        String out = tpl;
        for (var e : params.entrySet()) {
            out = out.replace("{" + e.getKey() + "}", String.valueOf(e.getValue()));
        }
        return out;
    }
}
