package com.apiframework.splunk;

import com.apiframework.model.ApiResponse;
import com.apiframework.splunk.config.SplunkConnectionConfig;
import com.apiframework.splunk.model.SplunkSearchResponse;

// Singleton entry point for Splunk in tests.
// Eliminates @BeforeClass/@AfterClass boilerplate and manual client construction in every test class.
public final class SplunkSupport {

    // Shared client initialised once from system properties. Session key is re-obtained on 401.
    private static final SplunkClient CLIENT =
        new SplunkClient(SplunkConnectionConfig.fromSystem());

    private SplunkSupport() {}

    // Returns the shared SplunkClient for tests that need to issue custom queries directly.
    public static SplunkClient client() {
        return CLIENT;
    }

    // High-level helper: extracts correlationId from the API response, builds the standard query,
    // and polls until logs appear. One line replaces query construction + await call in every test.
    public static SplunkSearchResponse awaitLogsFor(ApiResponse<?> response, String index) {
        String corrId = response.correlationId();
        String query = SplunkQueries.forCorrelationId(corrId, index);
        return CLIENT.awaitNonEmpty(query);
    }
}
