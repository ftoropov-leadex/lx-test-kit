package io.leadex.aqa.json;

import org.testng.annotations.Test;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the one shared ObjectMapper contract: single configured instance, unknown-property
 * tolerance, JavaTimeModule ISO-8601 handling, dates as ISO strings (not timestamps).
 */
public class JacksonProviderTest {

    @Test
    public void defaultMapper_returnsSameSingletonInstance() {
        assertThat(JacksonProvider.defaultMapper()).isSameAs(JacksonProvider.defaultMapper());
    }

    @Test
    public void deserializesJsonWithUnknownProperties() throws Exception {
        record Known(String name) {}
        var known = JacksonProvider.defaultMapper()
                .readValue("{\"name\":\"api\",\"unexpected\":42}", Known.class);
        assertThat(known.name()).isEqualTo("api");
    }

    @Test
    public void deserializesIso8601DateTime() throws Exception {
        OffsetDateTime parsed = JacksonProvider.defaultMapper()
                .readValue("\"2026-09-02T10:15:30+02:00\"", OffsetDateTime.class);
        assertThat(parsed).isEqualTo(OffsetDateTime.parse("2026-09-02T10:15:30+02:00"));
    }

    @Test
    public void serializesDatesAsIsoStringsNotTimestamps() throws Exception {
        OffsetDateTime dateTime = OffsetDateTime.parse("2026-09-02T10:15:30+02:00");
        assertThat(JacksonProvider.defaultMapper().writeValueAsString(dateTime))
                .isEqualTo("\"2026-09-02T10:15:30+02:00\"");
    }
}
