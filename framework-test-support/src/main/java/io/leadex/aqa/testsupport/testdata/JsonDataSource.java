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
 * Loads a JSON dataset file (classpath resource in the consumer repo) into a TestNG
 * {@code @DataProvider} table. Consumers keep the flat test pattern: a thin
 * {@code @DataProvider} method delegating to {@link #rows(String, String...)}, no
 * test-side helper classes.
 *
 * <p>Expected dataset shape — a JSON array of flat objects:
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
 * <p>{@code caseName} convention: when {@code "caseName"} is among the requested
 * columns, every row's value is validated to be a non-blank string, unique across
 * the dataset. It is passed through as an ordinary column — declared first in the
 * test signature it becomes the readable per-invocation anchor in reports, and row
 * identity survives dataset reordering.
 */
public final class JsonDataSource {

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

    private JsonDataSource() {}

    /**
     * Loads a JSON array of flat objects from the test classpath and returns
     * a TestNG data-provider table. Column order in the result matches the
     * {@code columns} argument order. Fails fast: per-row structural problems
     * name the resource, row index, and column; root-level parse failures name
     * the resource with Jackson's line/column detail.
     *
     * <p>JSON→Java type contract — test method parameters must declare the
     * matching types:
     *
     * <table border="1">
     *   <caption>JSON to Java type mapping</caption>
     *   <tr><th>JSON value</th><th>Java type in the row</th></tr>
     *   <tr><td>string</td><td>{@code String}</td></tr>
     *   <tr><td>integer</td><td>{@code Integer} / {@code Long}</td></tr>
     *   <tr><td>decimal</td><td>{@code BigDecimal}</td></tr>
     *   <tr><td>boolean</td><td>{@code Boolean}</td></tr>
     *   <tr><td>object</td><td>{@code Map<String,Object>} (deep-unmodifiable)</td></tr>
     *   <tr><td>array</td><td>{@code List<Object>} (deep-unmodifiable)</td></tr>
     *   <tr><td>explicit null</td><td>{@code null}</td></tr>
     * </table>
     *
     * <p>Two authoring rules for dataset JSON:
     * <ul>
     *   <li><b>Always quote string values.</b> An unquoted {@code 9900083901} binds as
     *       a numeric type and TestNG fails with a {@code MethodMatcherException} naming
     *       neither the file nor the row.</li>
     *   <li><b>Decimal columns are declared {@code BigDecimal} in the test signature,
     *       never {@code double}/{@code float}</b> — TestNG does no BigDecimal→double
     *       conversion.</li>
     * </ul>
     *
     * <p>Nested structures are returned deep-unmodifiable: TestNG retry re-invokes the
     * test with the same {@code Object[]}, so a mutating test would otherwise leak the
     * mutation into the retry attempt.
     *
     * @param classpathResource dataset path on the test classpath; may contain the
     *                          {@code {env}} token (resolved from {@code FRAMEWORK_ENV})
     * @param columns           columns to extract, in result order; at least one required
     * @return one {@code Object[]} per dataset row, in file order
     * @throws IllegalArgumentException if no columns are requested
     * @throws IllegalStateException    if {@code FRAMEWORK_ENV} is required but unset,
     *                                  the resource is missing, the JSON is malformed, or a
     *                                  row violates the structural / {@code caseName} contract
     */
    public static Object[][] rows(String classpathResource, String... columns) {
        if (columns == null || columns.length == 0) {
            throw new IllegalArgumentException("at least one column must be requested");
        }

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

        boolean caseNameRequested = List.of(columns).contains("caseName");
        Map<String, Integer> caseNameFirstRow = caseNameRequested ? new LinkedHashMap<>() : null;

        Object[][] rows = new Object[dataset.size()][];
        for (int i = 0; i < dataset.size(); i++) {
            Object element = dataset.get(i);
            if (!(element instanceof Map<?, ?> rowMap)) {
                throw new IllegalStateException("Dataset [" + path + "], row " + i
                        + ": expected an object but was " + jsonTypeName(element));
            }
            Object[] row = new Object[columns.length];
            for (int c = 0; c < columns.length; c++) {
                if (!rowMap.containsKey(columns[c])) {
                    throw new IllegalStateException("Dataset [" + path + "], row " + i
                            + ": missing column '" + columns[c] + "'");
                }
                row[c] = toDeepUnmodifiable(rowMap.get(columns[c]));
            }
            if (caseNameRequested) {
                validateCaseName(path, i, rowMap.get("caseName"), caseNameFirstRow);
            }
            rows[i] = row;
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
     * Copy-then-wrap in a single pass: each map/list is deep-copied recursively into a
     * fresh {@link LinkedHashMap}/{@link ArrayList} (key order preserved), then wrapped.
     * No reference to a mutable backing collection survives. {@code Map.copyOf} is
     * deliberately not used — it rejects null values, which datasets legitimately contain.
     * Scalars ({@code String}, {@code Integer}/{@code Long}, {@code BigDecimal},
     * {@code Boolean}, {@code null}) are immutable and pass through unchanged.
     */
    private static Object toDeepUnmodifiable(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            map.forEach((k, v) -> copy.put((String) k, toDeepUnmodifiable(v)));
            return Collections.unmodifiableMap(copy);
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            list.forEach(element -> copy.add(toDeepUnmodifiable(element)));
            return Collections.unmodifiableList(copy);
        }
        return value;
    }

    private static String jsonTypeName(Object value) {
        if (value == null)            return "null";
        if (value instanceof Map)     return "object";
        if (value instanceof List)    return "array";
        if (value instanceof String)  return "string";
        if (value instanceof Number)  return "number";
        if (value instanceof Boolean) return "boolean";
        return value.getClass().getSimpleName();
    }
}
