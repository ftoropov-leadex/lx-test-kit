package io.leadex.aqa.config;

import org.testng.annotations.Test;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pins the default wiring of {@link ConfigResolver}: env {@code dev}, 5000/15000 ms timeouts,
 * empty client credentials, and loud failure when {@code FRAMEWORK_BASE_URL} is absent.
 * Env vars are JVM-global — must never run inside a parallel TestNG suite (see plan Item 1 note).
 */
public class ConfigResolverTest {

    /** Removes all FRAMEWORK_* vars the resolver reads, so a dev-machine export cannot leak in. */
    private static EnvironmentVariables isolatedEnv() {
        return new EnvironmentVariables()
                .set("FRAMEWORK_ENV", null)
                .set("FRAMEWORK_BASE_URL", null)
                .set("FRAMEWORK_CONNECT_TIMEOUT", null)
                .set("FRAMEWORK_READ_TIMEOUT", null)
                .set("FRAMEWORK_CLIENT_NAME", null)
                .set("FRAMEWORK_CLIENT_SECRET", null);
    }

    @Test
    public void resolveFromSystem_returnsDefaultsWhenOnlyBaseUrlSet() throws Exception {
        isolatedEnv().set("FRAMEWORK_BASE_URL", "https://api.example").execute(() -> {
            var config = ConfigResolver.resolveFromSystem();
            assertThat(config.env()).isEqualTo("dev");
            assertThat(config.baseUrl()).isEqualTo("https://api.example");
            assertThat(config.connectTimeoutMs()).isEqualTo(5000);
            assertThat(config.readTimeoutMs()).isEqualTo(15000);
            assertThat(config.clientName()).isEmpty();
            assertThat(config.clientSecret()).isEmpty();
        });
    }

    @Test
    public void resolveFromSystem_throwsWhenBaseUrlMissing() throws Exception {
        isolatedEnv().execute(() -> {
            assertThatThrownBy(ConfigResolver::resolveFromSystem)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("FRAMEWORK_BASE_URL");
        });
    }

    @Test
    public void resolveFromSystem_readsAllValuesWhenSet() throws Exception {
        isolatedEnv()
                .set("FRAMEWORK_ENV", "staging")
                .set("FRAMEWORK_BASE_URL", "https://staging.example")
                .set("FRAMEWORK_CONNECT_TIMEOUT", "3000")
                .set("FRAMEWORK_READ_TIMEOUT", "8000")
                .set("FRAMEWORK_CLIENT_NAME", "svc-user")
                .set("FRAMEWORK_CLIENT_SECRET", "secret")
                .execute(() -> {
                    var config = ConfigResolver.resolveFromSystem();
                    assertThat(config.env()).isEqualTo("staging");
                    assertThat(config.baseUrl()).isEqualTo("https://staging.example");
                    assertThat(config.connectTimeoutMs()).isEqualTo(3000);
                    assertThat(config.readTimeoutMs()).isEqualTo(8000);
                    assertThat(config.clientName()).isEqualTo("svc-user");
                    assertThat(config.clientSecret()).isEqualTo("secret");
                });
    }
}
