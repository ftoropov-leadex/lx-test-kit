package com.apiframework.testsupport.base;

import com.apiframework.config.ConfigResolver;
import com.apiframework.config.EndpointDefinition;
import com.apiframework.config.EnvResolver;
import com.apiframework.config.FrameworkRuntimeConfig;
import com.apiframework.config.HttpVerb;
import com.apiframework.http.CorrelationIdFilter;
import com.apiframework.http.HttpClient;
import com.apiframework.http.RestAssuredHttpClient;
import com.apiframework.testsupport.client.ApiRequestBuilder;
import io.qameta.allure.Allure;
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

    /** The only domain binding a test must provide. Matches the {domain}.properties filename (without extension). */
    protected abstract String domain();

    @BeforeClass(alwaysRun = true)
    public void initHttpClient() {
        FrameworkRuntimeConfig config = ConfigResolver.resolveFromSystem();

        Properties domainProps = loadDomainProperties(domain());
        this.apiName   = domain();
        this.endpoints = parseEndpoints(domainProps);
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

        String clientName   = EnvResolver.string("FRAMEWORK_CLIENT_NAME", "");
        String clientSecret = EnvResolver.string("FRAMEWORK_CLIENT_SECRET", "");
        if (!clientName.isBlank() && !clientSecret.isBlank()) {
            specBuilder.setAuth(preemptive().basic(clientName, clientSecret));
        }

        this.httpClient = new RestAssuredHttpClient(specBuilder.build());
    }

    @BeforeMethod(alwaysRun = true)
    public void beforeEach(ITestResult result) {
        CorrelationIdFilter.set(UUID.randomUUID().toString());
        Allure.label("parentSuite", apiName);
    }

    @AfterMethod(alwaysRun = true)
    public void afterEach() {
        CorrelationIdFilter.clear();
    }

    protected <T> ApiRequestBuilder<T> call(String endpointKey, Class<T> responseType) {
        EndpointDefinition def = endpoints.get(endpointKey);
        if (def == null) {
            throw new IllegalStateException(
                "Unknown endpoint key '" + endpointKey + "' in domain '" + domain() + "'. " +
                "Known keys: " + new TreeSet<>(endpoints.keySet()));
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

    private static Properties loadDomainProperties(String domainName) {
        Properties all = new Properties();
        Path domainsPath = Path.of(EnvResolver.required("FRAMEWORK_DOMAINS_PATH"));
        try (InputStream in = Files.newInputStream(domainsPath)) {
            all.load(in);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load domains.properties from: " + domainsPath, e);
        }

        String prefix = domainName + ".";
        Properties domain = new Properties();
        for (String key : all.stringPropertyNames()) {
            if (key.startsWith(prefix)) {
                domain.setProperty(key.substring(prefix.length()), all.getProperty(key));
            }
        }
        if (domain.isEmpty()) {
            throw new IllegalStateException(
                "No properties found for domain '" + domainName + "' in domains.properties");
        }
        return domain;
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
