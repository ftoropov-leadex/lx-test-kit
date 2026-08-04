package io.leadex.aqa.testsupport.assertions;

import com.fasterxml.jackson.databind.JsonNode;
import org.assertj.core.api.AbstractAssert;

import java.math.BigDecimal;

/**
 * Fluent value-level assertions for a single JSON field, scoped inside the
 * {@code field(path, f -> ...)} consumer on {@link BodyAssert}.
 *
 * <p>All public methods are intercepted by {@code AllureAspectJ} via LTW — no manual
 * {@code Allure.step()} calls are needed.
 *
 * <p><b>Navigation-tolerant, validation-strict:</b> {@link BodyAssert#field(String, java.util.function.Consumer)}
 * never throws — it always produces a {@code FieldAssert} even when the field is absent. Each
 * terminal method here owns its missing-node semantics and fails with a field-specific message.
 *
 * <p>Terminal methods return {@code this} so multiple assertions can be chained on the same
 * field inside the consumer (e.g. {@code f -> f.isNotBlank().hasValue(x)}).
 *
 * <p>{@code matchesSchema} / {@code matchesSnapshot} are intentionally absent — they live
 * on {@link BodyAssert} only and always validate the full response.
 */
public final class FieldAssert extends AbstractAssert<FieldAssert, JsonNode> {

    private final String path;

    FieldAssert(JsonNode value, String path) {
        super(value, FieldAssert.class);
        this.path = path;
    }

    // ── Terminal assertions ──────────────────────────────────────────────────

    /**
     * Asserts the field exists and is non-null.
     */
    public FieldAssert isPresent() {
        if (actual.isMissingNode()) {
            failWithMessage("Expected field '%s' to be non-null but field was missing", path);
        }
        if (actual.isNull()) {
            failWithMessage("Expected field '%s' to be non-null but was null", path);
        }
        return this;
    }

    /**
     * Asserts the field exists and its value equals {@code expected}.
     *
     * <p>Supports {@code String}, {@code Integer}/{@code int}, {@code Long}/{@code long},
     * {@code Double}/{@code double}, {@code Boolean}/{@code boolean}, and {@code BigDecimal}
     * comparisons via type-aware extraction from the {@link JsonNode}.
     *
     * <p>{@code BigDecimal} expectations compare scale-insensitively against any numeric
     * node ({@code 7.745} matches a response carrying {@code 7.7450}) — required for
     * dataset decimals from {@code ExtractTestData}, which are always {@code BigDecimal}.
     */
    public FieldAssert hasValue(Object expected) {
        if (actual.isMissingNode()) {
            failWithMessage("Expected field '%s' to equal <%s> but field was missing", path, expected);
        }
        if (expected instanceof BigDecimal bd && actual.isNumber()) {
            // compareTo is scale-insensitive; decimalValue() yields the canonical decimal
            // (7.745, not the binary artifact), so response-side parsing needs no change
            if (actual.decimalValue().compareTo(bd) != 0) {
                failWithMessage("Expected field '%s' to equal <%s> but was <%s>", path, bd, actual.decimalValue());
            }
            return this;
        }
        Object actualValue = extractValue(actual);
        if (!expected.equals(actualValue)) {
            failWithMessage("Expected field '%s' to equal <%s> but was <%s>", path, expected, actualValue);
        }
        return this;
    }

    /**
     * Asserts the field exists and its text value is non-blank (non-null, non-empty,
     * non-whitespace-only).
     */
    public FieldAssert isNotBlank() {
        if (actual.isMissingNode()) {
            failWithMessage("Expected field '%s' to be non-blank but field was missing", path);
        }
        if (actual.isNull()) {
            failWithMessage("Expected field '%s' to be non-blank but was null", path);
        }
        if (actual.asText().isBlank()) {
            failWithMessage("Expected field '%s' to be non-blank but was: '%s'", path, actual.asText());
        }
        return this;
    }

    /**
     * Asserts the field exists and is non-empty.
     *
     * <p>For array fields: fails if the array is empty.
     * For string fields: fails if the string is empty.
     * For null or missing fields: always fails.
     */
    public FieldAssert isNotEmpty() {
        if (actual.isMissingNode()) {
            failWithMessage("Expected field '%s' to be non-empty but field was missing", path);
        }
        if (actual.isNull()) {
            failWithMessage("Expected field '%s' to be non-empty but was null", path);
        }
        if (actual.isArray() && actual.isEmpty()) {
            failWithMessage("Expected field '%s' to be non-empty array but was empty", path);
        }
        if (actual.isTextual() && actual.asText().isEmpty()) {
            failWithMessage("Expected field '%s' to be non-empty string but was empty", path);
        }
        return this;
    }

    // ── Internal ────────────────────────────────────────────────────────────

    private Object extractValue(JsonNode node) {
        if (node.isTextual())  return node.asText();
        if (node.isBoolean())  return node.asBoolean();
        if (node.isInt())      return node.asInt();
        if (node.isLong())     return node.asLong();
        if (node.isDouble())   return node.asDouble();
        return node.toString();
    }
}
