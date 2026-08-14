package io.leadex.aqa.testsupport.base;

import io.leadex.aqa.config.ConfigResolver;
import io.leadex.aqa.config.EndpointDefinition;
import io.leadex.aqa.config.EnvResolver;
import io.leadex.aqa.config.FrameworkRuntimeConfig;
import io.leadex.aqa.config.HttpVerb;
import io.leadex.aqa.http.CorrelationIdFilter;
import io.leadex.aqa.http.HttpClient;
import io.leadex.aqa.http.RestAssuredHttpClient;
import io.leadex.aqa.testsupport.client.ApiRequestBuilder;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.config.HttpClientConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.http.ContentType;
import org.testng.ITestResult;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.TreeSet;
import java.util.UUID;

import static io.restassured.RestAssured.preemptive;

public abstract class BaseApiTest {

    private HttpClient                      httpClient;
    private String                          baseUrl;
    private Map<String, EndpointDefinition> endpoints;
    private String                          apiName;

    protected abstract String domain();

    @BeforeClass(alwaysRun = true)
    public void initHttpClient() {
        FrameworkRuntimeConfig config = ConfigResolver.resolveFromSystem();

        Properties all = loadAllProperties();
        // Endpoint resolution: shared.endpoints.* is visible to every domain; the domain's
        // own entries win on a key collision (a domain may locally override a shared endpoint).
        Properties merged = extractPrefix(all, "shared", false);
        merged.putAll(extractPrefix(all, domain(), true));
        this.apiName   = domain();
        this.endpoints = parseEndpoints(merged);
        this.baseUrl   = config.baseUrl();

        RestAssuredConfig restAssuredConfig = RestAssuredConfig.config().httpClient(
            HttpClientConfig.httpClientConfig()
                .setParam("http.connection.timeout", config.connectTimeoutMs())
                .setParam("http.socket.timeout",     config.readTimeoutMs())
        );

        RequestSpecBuilder specBuilder = new RequestSpecBuilder()
            .setContentType(ContentType.JSON)
            .setConfig(restAssuredConfig)
            .addFilter(new CorrelationIdFilter());

        if (!config.clientName().isBlank() && !config.clientSecret().isBlank()) {
            specBuilder.setAuth(preemptive().basic(config.clientName(), config.clientSecret()));
        }

        this.httpClient = new RestAssuredHttpClient(specBuilder.build());
    }

    @BeforeMethod(alwaysRun = true)
    public void beforeEach(ITestResult result) {
        CorrelationIdFilter.set(UUID.randomUUID().toString());
    }

    @AfterMethod(alwaysRun = true)
    public void afterEach() {
        CorrelationIdFilter.clear();
    }

    protected <T> ApiRequestBuilder<T> call(String endpointKey, Class<T> responseType) {
        EndpointDefinition def = endpoints.get(endpointKey);
        if (def == null) {
            throw new IllegalStateException(
                "Unknown endpoint key '" + endpointKey + "' in domain '" + domain() + "' " +
                "or the 'shared' namespace. Known keys: " + new TreeSet<>(endpoints.keySet()));
        }
        return new ApiRequestBuilder<>(httpClient(), baseUrl, def, responseType);
    }

    /** Used by listeners and reporting for display metadata. */
    public final String apiName() { return apiName; }

    protected HttpClient httpClient() {
        if (httpClient == null) {
            throw new IllegalStateException(
                "httpClient is null — initHttpClient() did not run. Check @BeforeClass lifecycle setup.");
        }
        return httpClient;
    }

    private static Properties loadAllProperties() {
        Properties all = new Properties();
        Path domainsPath = Path.of(EnvResolver.required("FRAMEWORK_DOMAINS_PATH"));
        try (InputStream in = Files.newInputStream(domainsPath)) {
            all.load(in);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load domains.properties from: " + domainsPath, e);
        }
        return all;
    }

    /** Extracts the "{name}." sub-tree into a fresh Properties. A required namespace must
     *  contribute at least one property; the optional 'shared' namespace may be absent. */
    private static Properties extractPrefix(Properties all, String name, boolean required) {
        String prefix = name + ".";
        Properties out = new Properties();
        for (String key : all.stringPropertyNames()) {
            if (key.startsWith(prefix)) {
                out.setProperty(key.substring(prefix.length()), all.getProperty(key));
            }
        }
        if (required && out.isEmpty()) {
            throw new IllegalStateException(
                "No properties found for domain '" + name + "' in domains.properties");
        }
        return out;
    }

    private static Map<String, EndpointDefinition> parseEndpoints(Properties p) {
        Map<String, EndpointDefinition> out = new LinkedHashMap<>();
        for (String name : p.stringPropertyNames()) {
            if (!name.startsWith("endpoints.") || !name.endsWith(".method")) continue;
            String key    = name.substring("endpoints.".length(), name.length() - ".method".length());
            String method = p.getProperty("endpoints." + key + ".method");
            String relUrl = p.getProperty("endpoints." + key + ".relUrl");
            if (method == null || relUrl == null) {
                throw new IllegalStateException(
                    "Incomplete endpoint '" + key + "' in domain properties");
            }
            out.put(key, new EndpointDefinition(HttpVerb.valueOf(method.toUpperCase(Locale.ROOT)), relUrl));
        }
        return Map.copyOf(out);
    }
}
