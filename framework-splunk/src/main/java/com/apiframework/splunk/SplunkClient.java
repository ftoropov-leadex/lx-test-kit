package com.apiframework.splunk;

import com.apiframework.json.JacksonProvider;
import com.apiframework.splunk.config.SplunkConnectionConfig;
import com.apiframework.splunk.config.SplunkSearchConfig;
import com.apiframework.splunk.model.SplunkSearchResponse;
import com.apiframework.splunk.model.SplunkSearchResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.awaitility.Awaitility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

// Splunk REST API client. Manages session-key auth, executes one-shot and async job searches,
// and polls until results appear. Single class — no interface, no separate awaiter.
public final class SplunkClient implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(SplunkClient.class);

    private static final String AUTH_LOGIN_ENDPOINT = "/services/auth/login";
    private static final String SEARCH_JOBS_ENDPOINT = "/services/search/jobs";
    private static final String SEARCH_EXPORT_ENDPOINT = "/services/search/jobs/export";

    private final SplunkConnectionConfig connectionConfig;
    private final SplunkSearchConfig searchConfig;
    private final ObjectMapper objectMapper;
    private volatile String sessionKey;

    // Use when default search timing (SplunkSearchConfig.defaults()) is sufficient.
    public SplunkClient(SplunkConnectionConfig connectionConfig) {
        this(connectionConfig, SplunkSearchConfig.defaults());
    }

    // Use when custom poll interval or time window is needed.
    public SplunkClient(SplunkConnectionConfig connectionConfig, SplunkSearchConfig searchConfig) {
        this.connectionConfig = connectionConfig;
        this.searchConfig = searchConfig;
        this.objectMapper = JacksonProvider.defaultMapper();
    }

    // ── Search operations ───────────────────────────────────────────

    // Synchronous search via /services/search/jobs/export. Blocks until all results are returned.
    public SplunkSearchResponse searchOneShot(String splQuery, String earliestTime, String latestTime) {
        LOGGER.info("Executing one-shot Splunk search: {} [earliest={}, latest={}]",
            splQuery, earliestTime, latestTime);
        Response response = executeWithAuth(() ->
            baseRequest()
                .formParam("search", splQuery)
                .formParam("earliest_time", earliestTime)
                .formParam("latest_time", latestTime)
                .formParam("output_mode", "json")
                .post(connectionConfig.baseUrl() + SEARCH_EXPORT_ENDPOINT)
        );
        List<SplunkSearchResult> results = parseExportResults(response.getBody().asString());
        LOGGER.info("One-shot search returned {} result(s)", results.size());
        return new SplunkSearchResponse(results);
    }

    // Convenience overload using default time bounds from SplunkSearchConfig.
    public SplunkSearchResponse searchOneShot(String splQuery) {
        return searchOneShot(splQuery, searchConfig.defaultEarliestTime(), searchConfig.defaultLatestTime());
    }

    // Creates an async search job via /services/search/jobs. Returns the job SID for polling.
    public String createSearchJob(String splQuery, String earliestTime, String latestTime) {
        LOGGER.info("Creating Splunk search job: {} [earliest={}, latest={}]",
            splQuery, earliestTime, latestTime);
        Response response = executeWithAuth(() ->
            baseRequest()
                .formParam("search", splQuery)
                .formParam("earliest_time", earliestTime)
                .formParam("latest_time", latestTime)
                .formParam("output_mode", "json")
                .post(connectionConfig.baseUrl() + SEARCH_JOBS_ENDPOINT)
        );
        try {
            JsonNode root = objectMapper.readTree(response.getBody().asString());
            String sid = root.path("sid").asText();
            if (sid == null || sid.isBlank()) {
                throw new IllegalStateException("Splunk search job response did not contain a SID");
            }
            LOGGER.info("Splunk search job created with SID: {}", sid);
            return sid;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Unable to parse Splunk search job response", e);
        }
    }

    // Reads the current dispatchState of an async job by its SID.
    public SplunkJobStatus getJobStatus(String jobSid) {
        Response response = executeWithAuth(() ->
            baseRequest()
                .queryParam("output_mode", "json")
                .get(connectionConfig.baseUrl() + SEARCH_JOBS_ENDPOINT + "/" + jobSid)
        );
        try {
            JsonNode root = objectMapper.readTree(response.getBody().asString());
            String dispatchState = root.path("entry").path(0)
                .path("content").path("dispatchState").asText();
            return SplunkJobStatus.fromDispatchState(dispatchState);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to parse Splunk job status for SID: " + jobSid, e);
        }
    }

    // Fetches results of a DONE job. Call only after getJobStatus returns DONE.
    public SplunkSearchResponse getJobResults(String jobSid) {
        LOGGER.info("Retrieving results for Splunk job SID: {}", jobSid);
        Response response = executeWithAuth(() ->
            baseRequest()
                .queryParam("output_mode", "json")
                .queryParam("count", 0)
                .get(connectionConfig.baseUrl() + SEARCH_JOBS_ENDPOINT + "/" + jobSid + "/results")
        );
        List<SplunkSearchResult> results = parseJobResults(response.getBody().asString());
        LOGGER.info("Job {} returned {} result(s)", jobSid, results.size());
        return new SplunkSearchResponse(results);
    }

    // Creates a job, polls until DONE or FAILED, then returns results. Combines the three steps above.
    public SplunkSearchResponse searchBlocking(String splQuery, String earliestTime, String latestTime) {
        String sid = createSearchJob(splQuery, earliestTime, latestTime);
        long timeoutMs = searchConfig.awaitTimeout().toMillis();
        long pollMs = searchConfig.jobPollInterval().toMillis();
        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            SplunkJobStatus status = getJobStatus(sid);
            LOGGER.debug("Splunk job {} status: {}", sid, status);
            if (status.isTerminal()) {
                if (status == SplunkJobStatus.DONE) return getJobResults(sid);
                throw new IllegalStateException("Splunk search job failed. SID: " + sid);
            }
            try {
                Thread.sleep(pollMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for Splunk job: " + sid, e);
            }
        }
        throw new IllegalStateException(
            "Splunk search job timed out after " + searchConfig.awaitTimeout() + ". SID: " + sid
        );
    }

    // Convenience overload using default time bounds.
    public SplunkSearchResponse searchBlocking(String splQuery) {
        return searchBlocking(splQuery, searchConfig.defaultEarliestTime(), searchConfig.defaultLatestTime());
    }

    // ── Await operations ────────────────────────────────────────────

    // Polls searchOneShot repeatedly until the predicate is satisfied or timeout elapses.
    // Handles Splunk indexing latency — results may not appear immediately after an event.
    public SplunkSearchResponse awaitResults(
        String splQuery,
        String earliestTime,
        String latestTime,
        Predicate<SplunkSearchResponse> predicate
    ) {
        LOGGER.info("Awaiting Splunk results for query: {} [timeout={}, poll={}]",
            splQuery, searchConfig.awaitTimeout(), searchConfig.awaitPollInterval());
        AtomicReference<SplunkSearchResponse> captured = new AtomicReference<>();
        Awaitility.await("Splunk search: " + splQuery)
            .atMost(searchConfig.awaitTimeout())
            .pollInterval(searchConfig.awaitPollInterval())
            .pollInSameThread()
            .until(() -> {
                SplunkSearchResponse response = searchOneShot(splQuery, earliestTime, latestTime);
                if (predicate.test(response)) {
                    captured.set(response);
                    return true;
                }
                LOGGER.debug("Splunk search returned {} result(s), predicate not yet satisfied",
                    response.size());
                return false;
            });
        LOGGER.info("Splunk await completed with {} result(s)", captured.get().size());
        return captured.get();
    }

    // Convenience overload using default time bounds from SplunkSearchConfig.
    public SplunkSearchResponse awaitResults(String splQuery, Predicate<SplunkSearchResponse> predicate) {
        return awaitResults(
            splQuery,
            searchConfig.defaultEarliestTime(),
            searchConfig.defaultLatestTime(),
            predicate
        );
    }

    // Polls until at least one result exists. Most common await use case.
    public SplunkSearchResponse awaitNonEmpty(String splQuery) {
        return awaitResults(splQuery, response -> !response.isEmpty());
    }

    // Polls until at least one result has the given field value, then returns only matching results.
    public SplunkSearchResponse awaitResultsWithField(
        String splQuery, String fieldName, String fieldValue
    ) {
        SplunkSearchResponse response = awaitResults(
            splQuery,
            resp -> !resp.filterByField(fieldName, fieldValue).isEmpty()
        );
        return response.filterByField(fieldName, fieldValue);
    }

    @Override
    public void close() {
        LOGGER.debug("SplunkClient closed");
    }

    // ── Authentication ──────────────────────────────────────────────

    // POST /services/auth/login with username/password. Returns the session key.
    // Throws IllegalStateException on non-200 or missing sessionKey in response.
    private String authenticate() {
        LOGGER.info("Authenticating with Splunk at {}", connectionConfig.baseUrl());
        RequestSpecification spec = RestAssured.given();
        if (connectionConfig.allowUntrustedSsl()) {
            spec.relaxedHTTPSValidation();
        }
        Response response = spec
            .formParam("username", connectionConfig.username())
            .formParam("password", connectionConfig.password())
            .formParam("output_mode", "json")
            .post(connectionConfig.baseUrl() + AUTH_LOGIN_ENDPOINT);
        if (response.statusCode() != 200) {
            throw new IllegalStateException(
                "Splunk authentication failed with status " + response.statusCode()
                    + ": " + response.getBody().asString()
            );
        }
        try {
            JsonNode root = objectMapper.readTree(response.getBody().asString());
            String key = root.path("sessionKey").asText();
            if (key == null || key.isBlank()) {
                throw new IllegalStateException("Splunk authentication response did not contain sessionKey");
            }
            LOGGER.info("Splunk authentication successful");
            return key;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Unable to parse Splunk authentication response", e);
        }
    }

    // Executes the request with a valid session key. Re-authenticates once on 401 (session expiry).
    private Response executeWithAuth(RequestExecutor executor) {
        if (sessionKey == null) {
            sessionKey = authenticate();
        }
        Response response = executor.execute();
        if (response.statusCode() == 401) {
            LOGGER.info("Splunk session expired, re-authenticating");
            sessionKey = authenticate();
            response = executor.execute();
        }
        if (response.statusCode() >= 400) {
            throw new IllegalStateException(
                "Splunk REST API returned status " + response.statusCode()
                    + ": " + response.getBody().asString()
            );
        }
        return response;
    }

    // Builds a base RestAssured spec with the current session key and SSL config applied.
    private RequestSpecification baseRequest() {
        RequestSpecification spec = RestAssured.given()
            .header("Authorization", "Splunk " + sessionKey);
        if (connectionConfig.allowUntrustedSsl()) {
            spec.relaxedHTTPSValidation();
        }
        return spec;
    }

    // ── Response parsing ────────────────────────────────────────────

    // Parses NDJSON from /export endpoint — one JSON object per line, each with a "result" field.
    private List<SplunkSearchResult> parseExportResults(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return Collections.emptyList();
        }
        List<SplunkSearchResult> results = new ArrayList<>();
        for (String line : responseBody.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            try {
                JsonNode node = objectMapper.readTree(trimmed);
                if (node.has("result")) {
                    results.add(toSearchResult(node.get("result")));
                }
            } catch (Exception e) {
                LOGGER.debug("Skipping unparseable export line: {}", trimmed);
            }
        }
        return results;
    }

    // Parses the "results" array from an async job's result response.
    private List<SplunkSearchResult> parseJobResults(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return Collections.emptyList();
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode resultsNode = root.path("results");
            if (!resultsNode.isArray()) {
                return Collections.emptyList();
            }
            List<SplunkSearchResult> results = new ArrayList<>();
            for (JsonNode node : resultsNode) {
                results.add(toSearchResult(node));
            }
            return results;
        } catch (Exception e) {
            throw new IllegalStateException("Unable to parse Splunk job results", e);
        }
    }

    // Maps a single result JSON node to a SplunkSearchResult record.
    // Extracts well-known fields (_raw, _time, source, etc.) plus all remaining fields into a map.
    private SplunkSearchResult toSearchResult(JsonNode resultNode) {
        String raw = resultNode.path("_raw").asText(null);
        String timeStr = resultNode.path("_time").asText(null);
        String source = resultNode.path("source").asText(null);
        String sourceType = resultNode.path("sourcetype").asText(null);
        String host = resultNode.path("host").asText(null);
        String index = resultNode.path("index").asText(null);
        Instant time = parseTime(timeStr);
        Map<String, String> fields = new LinkedHashMap<>();
        for (Map.Entry<String, JsonNode> entry : resultNode.properties()) {
            fields.put(entry.getKey(), entry.getValue().asText());
        }
        return new SplunkSearchResult(raw, time, source, sourceType, host, index,
            Collections.unmodifiableMap(fields));
    }

    // Parses _time as ISO-8601 first, then falls back to epoch seconds (Splunk non-ISO formats).
    private static Instant parseTime(String timeStr) {
        if (timeStr == null || timeStr.isBlank()) return null;
        try {
            return Instant.parse(timeStr);
        } catch (DateTimeParseException e) {
            try {
                return Instant.ofEpochSecond(Long.parseLong(timeStr.split("\\.")[0]));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
    }

    @FunctionalInterface
    private interface RequestExecutor {
        Response execute();
    }

    // ── Nested enum ─────────────────────────────────────────────────

    // Maps Splunk job dispatchState strings to typed constants. Only used internally by this client.
    public enum SplunkJobStatus {
        QUEUED, PARSING, RUNNING, PAUSED, FINALIZING, DONE, FAILED, UNKNOWN;

        // Converts a raw dispatchState string from the REST API. Returns UNKNOWN for unrecognised values.
        public static SplunkJobStatus fromDispatchState(String dispatchState) {
            if (dispatchState == null || dispatchState.isBlank()) return UNKNOWN;
            try {
                return valueOf(dispatchState.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return UNKNOWN;
            }
        }

        // True when the job has reached a final state (no more status changes expected).
        public boolean isTerminal() {
            return this == DONE || this == FAILED;
        }
    }
}
