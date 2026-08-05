package io.leadex.aqa.testsupport.testdata;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * One row of a JSON dataset, returned by {@link ExtractTestData#from(String)} as the
 * single parameter of a TestNG data-driven test method. Typed values are extracted at
 * use sites — no per-column type declarations in the test signature:
 *
 * <pre>{@code
 * @Test(dataProvider = "customers")
 * public void customerMatchesDataset(DataRow row) {
 *     var response = call("get-customer", String.class)
 *             .pathParam("customerId", row.getString("customerId")).send();
 * }
 * }</pre>
 *
 * <p>Accessor contract (every failure names the dataset resource, row index, and field):
 * <ul>
 *   <li><b>Missing key</b> → {@link IllegalStateException}.</li>
 *   <li><b>Explicit {@code null} in the JSON</b> → {@code null} from every getter — a
 *       declared-absent value is a legitimate dataset state, distinguishable from a
 *       missing key. Passed to a request setter it is <b>sent</b>, not omitted
 *       (see {@code ApiRequestBuilder}'s explicit-null rule).</li>
 *   <li><b>Wrong type</b> → {@link IllegalStateException} naming expected and actual
 *       JSON type.</li>
 * </ul>
 *
 * <p>Integer getters accept {@code Integer}/{@code Long} (Jackson untyped parsing yields
 * both). {@link #getDecimal} accepts {@code BigDecimal} and integer types (widening),
 * never {@code double} — dataset decimals are always {@code BigDecimal} with the exact
 * written scale. Nested structures are deep-unmodifiable and pass through without copying.
 */
public final class DataRow {

    // Rendering-only mapper for toString() — never used for parsing; the loader's
    // strict read-side mapper in ExtractTestData is deliberately separate.
    private static final ObjectMapper TO_STRING_MAPPER = new ObjectMapper();

    private final String              resource;
    private final int                 rowIndex;
    private final Map<String, Object> values;

    /** Framework-internal: intended to be created only by {@link ExtractTestData}. */
    DataRow(String resource, int rowIndex, Map<String, Object> values) {
        this.resource = resource;
        this.rowIndex = rowIndex;
        this.values   = values;
    }

    /** Returns the field as a string, or {@code null} for an explicit JSON {@code null}. */
    public String getString(String field) {
        return typed(field, "string", String.class);
    }

    /** Returns the field as an int (accepts {@code Integer}/{@code Long}), or {@code null}. */
    public Integer getInt(String field) {
        Object v = integer(field);
        return v == null ? null : ((Number) v).intValue();
    }

    /** Returns the field as a long (accepts {@code Integer}/{@code Long}), or {@code null}. */
    public Long getLong(String field) {
        Object v = integer(field);
        return v == null ? null : ((Number) v).longValue();
    }

    /**
     * Returns the field as a {@link BigDecimal} (accepts {@code BigDecimal} and integer
     * types, widening; never {@code double}), or {@code null}.
     */
    public BigDecimal getDecimal(String field) {
        Object v = value(field);
        if (v == null)                return null;
        if (v instanceof BigDecimal d) return d;
        if (v instanceof Integer i)   return BigDecimal.valueOf(i);
        if (v instanceof Long l)      return BigDecimal.valueOf(l);
        throw wrongType(field, "decimal", v);
    }

    /** Returns the field as a boolean, or {@code null} for an explicit JSON {@code null}. */
    public Boolean getBoolean(String field) {
        return typed(field, "boolean", Boolean.class);
    }

    /** Returns the field as a deep-unmodifiable map, or {@code null}. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getMap(String field) {
        return typed(field, "object", Map.class);
    }

    /** Returns the field as a deep-unmodifiable list, or {@code null}. */
    @SuppressWarnings("unchecked")
    public List<Object> getList(String field) {
        return typed(field, "array", List.class);
    }

    /** Escape hatch: the raw value, including explicit {@code null}, for raw assertions. */
    public Object get(String field) {
        return value(field);
    }

    /**
     * Convenience for the {@code caseName} convention: the row's {@code caseName}
     * string, or {@code null} when the row has no {@code caseName} key.
     */
    public String caseName() {
        return values.containsKey("caseName") ? getString("caseName") : null;
    }

    /**
     * Compact JSON of the row, e.g. {@code {"caseName":"active","customerId":"9900083901"}}
     * — Allure shows data-provider parameters via {@code toString()}, so this is what a
     * per-invocation report entry reads like.
     */
    @Override
    public String toString() {
        try {
            return TO_STRING_MAPPER.writeValueAsString(values);
        } catch (JsonProcessingException e) {
            return values.toString();
        }
    }

    // ── Internal ────────────────────────────────────────────────────────────

    private Object value(String field) {
        if (!values.containsKey(field)) {
            throw new IllegalStateException("Dataset [" + resource + "], row " + rowIndex
                    + ": missing field '" + field + "'");
        }
        return values.get(field);
    }

    private Object integer(String field) {
        Object v = value(field);
        if (v == null || v instanceof Integer || v instanceof Long) return v;
        throw wrongType(field, "integer", v);
    }

    @SuppressWarnings("unchecked")
    private <T> T typed(String field, String jsonType, Class<?> type) {
        Object v = value(field);
        if (v == null) return null;
        if (type.isInstance(v)) return (T) v;
        throw wrongType(field, jsonType, v);
    }

    private IllegalStateException wrongType(String field, String expected, Object actual) {
        return new IllegalStateException("Dataset [" + resource + "], row " + rowIndex
                + ": field '" + field + "' expected " + expected
                + " but was " + ExtractTestData.jsonTypeName(actual));
    }
}
