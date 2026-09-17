package io.leadex.aqa.splunk.config;

import io.leadex.aqa.config.EnvResolver;

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

    /**
     * Reads the tunable subset from the environment, falling back to {@link #defaults()}
     * for everything unset — behaviour is byte-identical to {@code defaults()} when no
     * variable is present. Both variables are optional; a required one would break every
     * consumer run that does not set it.
     */
    public static SplunkSearchConfig fromSystem() {
        SplunkSearchConfig fallback = defaults();
        return new SplunkSearchConfig(
            EnvResolver.string("SPLUNK_EARLIEST_TIME", fallback.defaultEarliestTime()),
            fallback.defaultLatestTime(),
            Duration.ofSeconds(EnvResolver.integer(
                "SPLUNK_AWAIT_TIMEOUT_S", (int) fallback.awaitTimeout().toSeconds())),
            fallback.awaitPollInterval(),
            fallback.jobPollInterval()
        );
    }
}
