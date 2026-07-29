package io.leadex.aqa.splunk;

import io.leadex.aqa.model.ApiResponse;
import io.leadex.aqa.splunk.config.SplunkConnectionConfig;
import io.leadex.aqa.splunk.model.SplunkSearchResponse;

// Singleton entry point for Splunk in tests.
// Eliminates @BeforeClass/@AfterClass boilerplate and manual client construction in every test class.
public final class SplunkSupport {

    // Initialization-on-demand holder: the client is built on first actual use, not at class load.
    // Suites that never call Splunk no longer require SPLUNK_* env vars to be present; a missing
    // var surfaces at the first real Splunk call as IllegalStateException naming the var.
    private static final class Holder {
        private static final SplunkClient CLIENT =
            new SplunkClient(SplunkConnectionConfig.fromSystem());
    }

    private SplunkSupport() {}

    // Returns the shared SplunkClient for tests that need to issue custom queries directly.
    public static SplunkClient client() {
        return Holder.CLIENT;
    }

    // High-level helper: extracts correlationId from the API response, builds the standard query,
    // and polls until logs appear. One line replaces query construction + await call in every test.
    public static SplunkSearchResponse awaitLogsFor(ApiResponse<?> response, String index) {
        String corrId = response.correlationId();
        String query = SplunkQueries.forCorrelationId(corrId, index);
        return client().awaitNonEmpty(query);
    }
}
