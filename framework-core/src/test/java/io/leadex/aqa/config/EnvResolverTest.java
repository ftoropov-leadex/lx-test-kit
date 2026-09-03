package io.leadex.aqa.config;

import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static uk.org.webcompere.systemstubs.SystemStubs.withEnvironmentVariable;

/**
 * Env contract: trim, blank-means-absent, defaults, loud failure on missing-required and on
 * garbage int. Env vars are JVM-global — system-stubs sets/removes them per test; this class
 * must never run inside a parallel TestNG suite (see plan Item 1 note).
 */
public class EnvResolverTest {

    @Test
    public void required_throwsWhenAbsentOrBlank() throws Exception {
        withEnvironmentVariable("LX_TEST_REQUIRED", null).execute(() -> {
            assertThatThrownBy(() -> EnvResolver.required("LX_TEST_REQUIRED"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("LX_TEST_REQUIRED");
        });
        withEnvironmentVariable("LX_TEST_REQUIRED", "   ").execute(() -> {
            assertThatThrownBy(() -> EnvResolver.required("LX_TEST_REQUIRED"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("LX_TEST_REQUIRED");
        });
    }

    @Test
    public void required_returnsTrimmedValue() throws Exception {
        withEnvironmentVariable("LX_TEST_REQUIRED", " value ").execute(() -> {
            assertThat(EnvResolver.required("LX_TEST_REQUIRED")).isEqualTo("value");
        });
    }

    @Test
    public void string_returnsDefaultWhenAbsentOrBlank() throws Exception {
        withEnvironmentVariable("LX_TEST_STRING", null).execute(() -> {
            assertThat(EnvResolver.string("LX_TEST_STRING", "fallback")).isEqualTo("fallback");
        });
        withEnvironmentVariable("LX_TEST_STRING", "   ").execute(() -> {
            assertThat(EnvResolver.string("LX_TEST_STRING", "fallback")).isEqualTo("fallback");
        });
    }

    @Test
    public void string_returnsTrimmedValue() throws Exception {
        withEnvironmentVariable("LX_TEST_STRING", " hello ").execute(() -> {
            assertThat(EnvResolver.string("LX_TEST_STRING", "fallback")).isEqualTo("hello");
        });
    }

    @Test
    public void integer_returnsDefaultWhenAbsentOrBlank() throws Exception {
        withEnvironmentVariable("LX_TEST_INT", null).execute(() -> {
            assertThat(EnvResolver.integer("LX_TEST_INT", 42)).isEqualTo(42);
        });
        withEnvironmentVariable("LX_TEST_INT", "  ").execute(() -> {
            assertThat(EnvResolver.integer("LX_TEST_INT", 42)).isEqualTo(42);
        });
    }

    @Test
    public void integer_parsesValidValue() throws Exception {
        withEnvironmentVariable("LX_TEST_INT", " 8080 ").execute(() -> {
            assertThat(EnvResolver.integer("LX_TEST_INT", 42)).isEqualTo(8080);
        });
    }

    @Test
    public void integer_throwsOnGarbage() throws Exception {
        withEnvironmentVariable("LX_TEST_INT", "abc").execute(() -> {
            assertThatThrownBy(() -> EnvResolver.integer("LX_TEST_INT", 42))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("LX_TEST_INT")
                    .hasMessageContaining("abc");
        });
    }

    @Test
    public void bool_returnsDefaultWhenAbsentOrBlankAndParsesValues() throws Exception {
        withEnvironmentVariable("LX_TEST_BOOL", null).execute(() -> {
            assertThat(EnvResolver.bool("LX_TEST_BOOL", true)).isTrue();
        });
        withEnvironmentVariable("LX_TEST_BOOL", "  ").execute(() -> {
            assertThat(EnvResolver.bool("LX_TEST_BOOL", true)).isTrue();
        });
        withEnvironmentVariable("LX_TEST_BOOL", "true").execute(() -> {
            assertThat(EnvResolver.bool("LX_TEST_BOOL", false)).isTrue();
        });
        withEnvironmentVariable("LX_TEST_BOOL", "false").execute(() -> {
            assertThat(EnvResolver.bool("LX_TEST_BOOL", true)).isFalse();
        });
    }
}
