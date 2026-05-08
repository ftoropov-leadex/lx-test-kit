package com.apiframework.splunk.config;

import java.time.Duration;

// Search timing parameters: time window for queries and poll/timeout intervals for await operations.
// Pass a custom instance to SplunkClient when the defaults are too tight or too loose.
public record SplunkSearchConfig(
    String defaultEarliestTime,  // SPL earliest_time, e.g. "-15m"
    String defaultLatestTime,    // SPL latest_time, e.g. "now"
    Duration awaitTimeout,       // How long awaitNonEmpty/awaitResults will poll before failing
    Duration awaitPollInterval,  // Delay between one-shot search attempts during await
    Duration jobPollInterval     // Delay between dispatchState polls for async jobs
) {

    // Sensible defaults for most test scenarios: 15-minute window, 60s await, 3s poll.
    public static SplunkSearchConfig defaults() {
        return new SplunkSearchConfig(
            "-15m",
            "now",
            Duration.ofSeconds(60),
            Duration.ofSeconds(3),
            Duration.ofSeconds(2)
        );
    }
}
