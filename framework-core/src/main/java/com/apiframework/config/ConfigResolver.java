package com.apiframework.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ConfigResolver {

    private static final Logger log = LoggerFactory.getLogger(ConfigResolver.class);

    private ConfigResolver() {}

    public static FrameworkRuntimeConfig resolveFromSystem() {
        var config = new FrameworkRuntimeConfig(
            EnvResolver.string("FRAMEWORK_ENV", "dev"),
            EnvResolver.required("FRAMEWORK_BASE_URL"),
            EnvResolver.integer("FRAMEWORK_CONNECT_TIMEOUT", 5000),
            EnvResolver.integer("FRAMEWORK_READ_TIMEOUT", 15000),
            EnvResolver.string("FRAMEWORK_CLIENT_NAME", ""),
            EnvResolver.string("FRAMEWORK_CLIENT_SECRET", "")
        );
        log.debug("Framework config: env={}, baseUrl={}, timeouts={}/{}, auth={}",
            config.env(), config.baseUrl(), config.connectTimeoutMs(), config.readTimeoutMs(),
            config.clientName().isBlank() ? "none" : "configured");
        return config;
    }
}
