package com.apiframework.testsupport.contracts;

import com.apiframework.json.JacksonProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;

import java.io.InputStream;
import java.util.Set;

/**
 * Validates a JSON response body against a JSON Schema definition loaded from the classpath.
 * Catches structural drift: missing fields, type changes, unexpected properties.
 *
 * <p>Allure step generation is handled by {@code AllureAspectJ} intercepting the
 * {@code matchesSchema()} call on the DSL chain — no manual {@code Allure.step()} needed here.
 */
public final class JsonSchemaContractValidator {
    private final ObjectMapper objectMapper;

    public JsonSchemaContractValidator() {
        this.objectMapper = JacksonProvider.defaultMapper();
    }

    public void assertMatchesSchema(String payloadJson, String classpathSchemaLocation) {
        try (InputStream schemaStream = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(classpathSchemaLocation)) {
            if (schemaStream == null) {
                throw new IllegalArgumentException("Schema not found in classpath: " + classpathSchemaLocation);
            }
            JsonNode schemaNode = objectMapper.readTree(schemaStream);
            JsonNode payloadNode = objectMapper.readTree(payloadJson);

            JsonSchemaFactory schemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
            JsonSchema schema = schemaFactory.getSchema(schemaNode);
            Set<ValidationMessage> errors = schema.validate(payloadNode);

            if (!errors.isEmpty()) {
                throw new AssertionError("Schema validation failed: " + errors);
            }
        } catch (AssertionError assertionError) {
            throw assertionError;
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to validate JSON schema", exception);
        }
    }
}
