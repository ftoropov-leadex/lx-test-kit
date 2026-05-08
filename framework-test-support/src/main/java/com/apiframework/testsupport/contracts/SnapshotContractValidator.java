package com.apiframework.testsupport.contracts;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;

/**
 * Validates a JSON response body against a golden-file snapshot loaded from the classpath.
 * Catches value-level drift: changed field values, reordered keys, new unexpected fields.
 *
 * <p>Snapshots are loaded via classpath — place them in the test suite's
 * {@code src/test/resources/snapshots/} directory alongside schema files.
 *
 * <p>Allure step generation is handled by {@code AllureAspectJ} intercepting the
 * {@code matchesSnapshot()} call on the DSL chain — no manual {@code Allure.step()} needed here.
 */
public final class SnapshotContractValidator {
    private final String classpathRoot;

    /**
     * @param classpathRoot classpath prefix for snapshot files, e.g. {@code "snapshots/"}
     */
    public SnapshotContractValidator(String classpathRoot) {
        this.classpathRoot = classpathRoot.endsWith("/") ? classpathRoot : classpathRoot + "/";
    }

    public SnapshotContractValidator() {
        this("snapshots/");
    }

    public void assertMatchesSnapshot(String snapshotName, String actualJson) {
        String resourcePath = classpathRoot + snapshotName + ".json";
        try (InputStream stream = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(resourcePath)) {
            if (stream == null) {
                throw new AssertionError("Snapshot not found on classpath: " + resourcePath);
            }
            String expectedJson = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            assertThatJson(actualJson).isEqualTo(expectedJson);
        } catch (AssertionError assertionError) {
            throw assertionError;
        } catch (IOException ioException) {
            throw new IllegalStateException("Unable to read snapshot file: " + resourcePath, ioException);
        }
    }
}
