package io.leadex.aqa.testsupport.testdata;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.leadex.aqa.config.EnvResolver;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Extracts test data from a JSON dataset file (classpath resource in the consumer repo)
 * into a TestNG {@code @DataProvider} table — one {@link DataRow} per dataset object.
 * Consumers keep the flat test pattern: a thin {@code @DataProvider} method delegating
 * to {@link #from(String)}, no test-side helper classes:
 *
 * <pre>{@code
 * @DataProvider(name = "customers")
 * public Object[][] customers() {
 *     return ExtractTestData.from("data/{env}/customers.json");
 * }
 *
 * @Test(dataProvider = "customers")
 * public void customerMatchesDataset(DataRow row) {
 *     var response = call("get-customer", String.class)
 *             .pathParam("customerId", row.getString("customerId")).send();
 * }
 * }</pre>
 *
 * <p>Never static-import {@code from} — the qualified form is what reads as a sentence.
 *
 * <p>Expected dataset shape — a JSON array of flat-keyed objects (top-level keys are
 * the fields a {@link DataRow} exposes; values may be any JSON, including nested
 * objects/arrays):
 * <pre>{@code
 * [
 *   { "customerId": "9900083901", "expectedStatus": "ACTIVE" },
 *   { "customerId": "9900083902", "expectedStatus": "BLOCKED" }
 * ]
 * }</pre>
 *
 * <p>Environment-specific datasets: if the resource path contains the literal token
 * {@code {env}}, every occurrence is replaced with the value of the existing
 * {@code FRAMEWORK_ENV} env var (resolved via {@link EnvResolver#required} — no
 * fallback, an unset var fails fast before any I/O). No token → the path is used
 * as-is.
 *
 * <p>{@code caseName} convention: whenever a row contains a {@code "caseName"} key, its
 * value is validated to be a non-blank string, unique across the dataset. Rows without
 * the key are allowed, but including it remains the recommended practice — it anchors
 * the readable per-invocation entry in reports ({@link DataRow#toString()} /
 * {@link DataRow#caseName()}) and row identity survives dataset reordering.
 */
public final class ExtractTestData {

    // Private read-side-only mapper — never reuse or mutate JacksonProvider.defaultMapper()
    // (shared static; enabling parser features on it is global-state mutation).
    // Datasets are parsed untyped (readValue to Object): the tree model (readTree)
    // normalizes DecimalNode values and would turn 100.00 into 1E+2, while untyped
    // parsing keeps the exact written scale.
    private static final ObjectMapper MAPPER = JsonMapper.builder()
            // QA copy-paste duplicate key fails the run instead of silently overwriting
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            // money amounts keep exact written scale — 100.00 must not become 100.0
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .build();

    private ExtractTestData() {}

    /**
     * Loads a JSON array of flat-keyed objects from the test classpath and returns
     * a TestNG data-provider table — one {@link DataRow} per dataset object, in file
     * order. Typed values are extracted via the {@link DataRow} getters at use sites;
     * see {@link DataRow} for the JSON→Java type contract and the missing-key /
     * explicit-null / wrong-type accessor semantics.
     *
     * <p>Nested structures are returned deep-unmodifiable: TestNG retry re-invokes the
     * test with the same {@code Object[]}, so a mutating test would otherwise leak the
     * mutation into the retry attempt.
     *
     * <p>Faker placeholders: any <b>string</b> value containing a native DataFaker
     * {@code #{...}} expression (e.g. {@code "#{numerify '+7##########'}"}) — at any
     * nesting depth — is resolved once here, at load time, via {@link Fake#expression}.
     * Placeholders always resolve to strings; a typed read is one line test-side
     * ({@code new BigDecimal(row.getString("amount"))}). Non-string values and explicit
     * JSON {@code null} pass through untouched. Resolution happens before
     * {@code caseName} validation, so a {@code caseName} containing an expression is
     * validated in resolved form (uniqueness included). Resolve-once-at-load means
     * every retry attempt and every report entry sees identical values; a run is
     * reproduced by re-running with {@code FRAMEWORK_FAKER_SEED} set to the seed logged
     * at init (see {@link Fake}). An expression DataFaker rejects fails the load with
     * an {@link IllegalStateException} naming the dataset, row, and field — loud at
     * provider time, before any HTTP call.
     *
     * @param classpathResource dataset path on the test classpath; may contain the
     *                          {@code {env}} token (resolved from {@code FRAMEWORK_ENV})
     * @return one single-element {@code Object[]} (a {@link DataRow}) per dataset row,
     *         in file order; an empty dataset returns zero rows (TestNG skips the test)
     * @throws IllegalStateException if {@code FRAMEWORK_ENV} is required but unset,
     *                               the resource is missing, the JSON is malformed, a
     *                               placeholder expression is rejected, or a row
     *                               violates the structural / {@code caseName} contract
     */
    public static Object[][] from(String classpathResource) {
        String path = classpathResource.contains("{env}")
                ? classpathResource.replace("{env}", EnvResolver.required("FRAMEWORK_ENV"))
                : classpathResource;

        Object parsed = readDataset(path);

        if (!(parsed instanceof List<?> dataset)) {
            throw new IllegalStateException("Failed to parse dataset [" + path
                    + "]: root must be a JSON array of objects but was " + jsonTypeName(parsed));
        }
        if (dataset.isEmpty()) {
            // intentionally emptied dataset is a valid QA state — TestNG skips the test
            return new Object[0][];
        }

        Map<String, Integer> caseNameFirstRow = new LinkedHashMap<>();

        Object[][] rows = new Object[dataset.size()][];
        for (int i = 0; i < dataset.size(); i++) {
            Object element = dataset.get(i);
            if (!(element instanceof Map<?, ?> rowMap)) {
                throw new IllegalStateException("Dataset [" + path + "], row " + i
                        + ": expected an object but was " + jsonTypeName(element));
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> rowValues = (Map<String, Object>) toDeepUnmodifiable(path, i, null, rowMap);
            if (rowValues.containsKey("caseName")) {
                validateCaseName(path, i, rowValues.get("caseName"), caseNameFirstRow);
            }
            rows[i] = new Object[]{ new DataRow(path, i, rowValues) };
        }
        return rows;
    }

    // ── Internal ────────────────────────────────────────────────────────────

    private static Object readDataset(String path) {
        try (InputStream stream = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalStateException("Dataset not found on classpath: " + path);
            }
            return MAPPER.readValue(stream, Object.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to parse dataset [" + path + "]: "
                    + e.getOriginalMessage() + " at " + e.getLocation(), e);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read dataset [" + path + "]: "
                    + e.getMessage(), e);
        }
    }

    private static void validateCaseName(String path, int rowIndex, Object caseName,
                                         Map<String, Integer> caseNameFirstRow) {
        if (!(caseName instanceof String name) || name.isBlank()) {
            throw new IllegalStateException("Dataset [" + path + "], row " + rowIndex
                    + ": column 'caseName' must be a non-blank string but was " + caseName);
        }
        Integer firstRow = caseNameFirstRow.putIfAbsent(name, rowIndex);
        if (firstRow != null) {
            throw new IllegalStateException("Dataset [" + path + "], rows " + firstRow
                    + " and " + rowIndex + ": duplicate caseName '" + name + "'");
        }
    }

    /**
     * Copy-resolve-wrap in a single pass: each map/list is deep-copied recursively into a
     * fresh {@link LinkedHashMap}/{@link ArrayList} (key order preserved), then wrapped.
     * No reference to a mutable backing collection survives. {@code Map.copyOf} is
     * deliberately not used — it rejects null values, which datasets legitimately contain.
     * String values containing {@code #{} are resolved as DataFaker expressions (once,
     * here at load); other scalars ({@code Integer}/{@code Long}, {@code BigDecimal},
     * {@code Boolean}, {@code null}) are immutable and pass through unchanged.
     *
     * @param field the key the value sits under (for error messages); {@code null} only
     *              for the top-level row object, which is a map and never resolved itself
     */
    private static Object toDeepUnmodifiable(String path, int rowIndex, String field, Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            map.forEach((k, v) -> copy.put((String) k, toDeepUnmodifiable(path, rowIndex, (String) k, v)));
            return Collections.unmodifiableMap(copy);
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            list.forEach(element -> copy.add(toDeepUnmodifiable(path, rowIndex, field, element)));
            return Collections.unmodifiableList(copy);
        }
        if (value instanceof String string && string.contains("#{")) {
            try {
                return Fake.expression(string);
            } catch (RuntimeException e) {
                throw new IllegalStateException("Dataset [" + path + "], row " + rowIndex
                        + ", field '" + field + "': DataFaker rejected expression '" + string
                        + "': " + e.getMessage(), e);
            }
        }
        return value;
    }

    static String jsonTypeName(Object value) {
        if (value == null)            return "null";
        if (value instanceof Map)     return "object";
        if (value instanceof List)    return "array";
        if (value instanceof String)  return "string";
        if (value instanceof Number)  return "number";
        if (value instanceof Boolean) return "boolean";
        return value.getClass().getSimpleName();
    }
}
