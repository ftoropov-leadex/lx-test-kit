package io.leadex.aqa.testsupport.testdata;

import com.sun.net.httpserver.HttpServer;
import io.leadex.aqa.config.EndpointDefinition;
import io.leadex.aqa.config.HttpVerb;
import io.leadex.aqa.http.RestAssuredHttpClient;
import io.leadex.aqa.testsupport.client.ApiRequestBuilder;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BigDecimal round-trip proof (task A7): dataset parse keeps the exact written decimal
 * scale, and {@code .bodyField} writes that exact representation on the wire —
 * {@code 7.745} stays {@code 7.745}, {@code 100.00} stays {@code 100.00}
 * (never {@code 100.0}, never {@code 1E+2}).
 *
 * <p>Self-contained by design: JDK-native mock server, no {@code BaseApiTest},
 * no env vars, no {@code domains.properties}.
 */
public class JsonDataSourceTest {

    private static final String DATASET = "data/bigdecimal-rows.json";

    private HttpServer server;
    private String baseUrl;
    private final List<String> capturedBodies = new CopyOnWriteArrayList<>();

    @BeforeClass
    public void startMock() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/capture", exchange -> {
            capturedBodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] ok = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, ok.length);
            exchange.getResponseBody().write(ok);
            exchange.close();
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterClass(alwaysRun = true)
    public void stopMock() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    public void datasetDecimalsArriveAsExactScaleBigDecimal() {
        Object[][] rows = JsonDataSource.rows(DATASET, "caseName", "amount");

        assertThat(rows.length).isEqualTo(2);
        assertThat(rows[0][1]).isInstanceOf(BigDecimal.class);
        assertThat(rows[0][1].toString()).isEqualTo("7.745");
        assertThat(rows[1][1]).isInstanceOf(BigDecimal.class);
        assertThat(rows[1][1].toString()).isEqualTo("100.00");
    }

    @Test
    public void bodyFieldWritesExactDecimalOnTheWire() {
        Object[][] rows = JsonDataSource.rows(DATASET, "caseName", "amount");
        RestAssuredHttpClient client = new RestAssuredHttpClient(
                new RequestSpecBuilder().setContentType(ContentType.JSON).build());
        EndpointDefinition capture = new EndpointDefinition(HttpVerb.POST, "/capture");

        for (Object[] row : rows) {
            new ApiRequestBuilder<>(client, baseUrl, capture, String.class)
                    .bodyField("amount", row[1])
                    .send();
        }

        assertThat(capturedBodies).containsExactly(
                "{\"amount\":7.745}",
                "{\"amount\":100.00}");
    }
}
