package com.apiframework.splunk.config;

import com.apiframework.config.EnvResolver;

public record SplunkConnectionConfig(
    String baseUrl,
    String username,
    String password,
    boolean allowUntrustedSsl
) {

    public static SplunkConnectionConfig fromSystem() {
        return new SplunkConnectionConfig(
            EnvResolver.required("SPLUNK_BASE_URL"),
            EnvResolver.string("SPLUNK_USERNAME", ""),
            EnvResolver.string("SPLUNK_PASSWORD", ""),
            EnvResolver.bool("SPLUNK_ALLOW_UNTRUSTED_SSL", true)
        );
    }
}
