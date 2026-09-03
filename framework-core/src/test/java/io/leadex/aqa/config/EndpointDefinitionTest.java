package io.leadex.aqa.config;

import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pins the {@code {method, relUrl}} guards every endpoint definition carries: both components
 * required, relUrl must not be blank.
 */
public class EndpointDefinitionTest {

    @Test
    public void constructsWithValidArguments() {
        var def = new EndpointDefinition(HttpVerb.POST, "/v1/users");
        assertThat(def.method()).isEqualTo(HttpVerb.POST);
        assertThat(def.relUrl()).isEqualTo("/v1/users");
    }

    @Test
    public void rejectsNullMethod() {
        assertThatThrownBy(() -> new EndpointDefinition(null, "/v1/users"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("method");
    }

    @Test
    public void rejectsBlankRelUrl() {
        assertThatThrownBy(() -> new EndpointDefinition(HttpVerb.GET, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("relUrl");
        assertThatThrownBy(() -> new EndpointDefinition(HttpVerb.GET, "   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("relUrl");
    }
}
