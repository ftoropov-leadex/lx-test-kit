package com.apiframework.testsupport.assertions;

import com.fasterxml.jackson.databind.JsonNode;
import org.assertj.core.api.AbstractAssert;

/**
 * Fluent value-level assertions for a single JSON field reached via
 * {@link BodyAssert#field(String)}.
 *
 * <p>All public methods are intercepted by {@code AllureAspectJ} via LTW — no manual
 * {@code Allure.step()} calls are needed.
 *
 * <p><b>Navigation-tolerant, validation-strict:</b> {@link BodyAssert#field(String)} never
 * throws — it always produces a {@code FieldAssert} even when the field is absent. Each
 * terminal method here owns its missing-node semantics and fails with a field-specific message.
 *
 * <p>Terminal methods return the parent {@link BodyAssert} to allow further field assertions
 * or contract validation to be chained without an explicit {@code .end()} call.
 *
 * <p>{@code matchesSchema} / {@code matchesSnapshot} are intentionally absent — they live
 * on {@link BodyAssert} only and always validate the full response.
 */
public final class FieldAssert extends AbstractAssert<FieldAssert, JsonNode> {

    private final BodyAssert parent;
    private final String path;

    FieldAssert(JsonNode value, BodyAssert parent, String path) {
        super(value, FieldAssert.class);
        this.parent = parent;
        this.path = path;
    }

    // ── Terminal assertions ──────────────────────────────────────────────────

    /**
     * Asserts the field exists and is non-null.
     */
    public BodyAssert isPresent() {
        if (actual.isMissingNode()) {
            failWithMessage("Expected field '%s' to be non-null but field was missing", path);
        }
        if (actual.isNull()) {
            failWithMessage("Expected field '%s' to be non-null but was null", path);
        }
        return parent;
    }

    /**
     * Asserts the field exists and its value equals {@code expected}.
     *
     * <p>Supports {@code String}, {@code Integer}/{@code int}, {@code Long}/{@code long},
     * {@code Double}/{@code double}, and {@code Boolean}/{@code boolean} comparisons via
     * type-aware extraction from the {@link JsonNode}.
     */
    public BodyAssert hasValue(Object expected) {
        if (actual.isMissingNode()) {
            failWithMessage("Expected field '%s' to equal <%s> but field was missing", path, expected);
        }
        Object actualValue = extractValue(actual);
        if (!expected.equals(actualValue)) {
            failWithMessage("Expected field '%s' to equal <%s> but was <%s>", path, expected, actualValue);
        }
        return parent;
    }

    /**
     * Asserts the field exists and its text value is non-blank (non-null, non-empty,
     * non-whitespace-only).
     */
    public BodyAssert isNotBlank() {
        if (actual.isMissingNode()) {
            failWithMessage("Expected field '%s' to be non-blank but field was missing", path);
        }
        if (actual.isNull()) {
            failWithMessage("Expected field '%s' to be non-blank but was null", path);
        }
        if (actual.asText().isBlank()) {
            failWithMessage("Expected field '%s' to be non-blank but was: '%s'", path, actual.asText());
        }
        return parent;
    }

    /**
     * Asserts the field exists and is non-empty.
     *
     * <p>For array fields: fails if the array is empty.
     * For string fields: fails if the string is empty.
     * For null or missing fields: always fails.
     */
    public BodyAssert isNotEmpty() {
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
        return parent;
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
