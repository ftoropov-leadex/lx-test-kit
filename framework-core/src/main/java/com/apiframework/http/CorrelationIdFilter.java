package com.apiframework.http;

import io.restassured.filter.Filter;
import io.restassured.filter.FilterContext;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.FilterableResponseSpecification;
//Three responsibilities, all real:
//
//1. Holds the correlation ID per thread — ThreadLocal ensures tests running in parallel don't bleed IDs into each other's requests
//2. Injects it into every outgoing request as X-Correlation-Id header — REST Assured filter intercepts the request before it hits the wire
//3. Lifecycle managed by BaseApiTest — set() in @BeforeMethod, clear() in @AfterMethod prevents leaks between tests
//The ID itself is picked up by RestAssuredHttpClient.toApiResponse() from the response headers and stored in ApiResponse.correlationId(), so it's traceable end-to-end.






public class CorrelationIdFilter implements Filter {
    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    public static void set(String id) {
        CURRENT.set(id);
    }

    public static void clear() {
        CURRENT.remove();
    }

    @Override
    public Response filter(FilterableRequestSpecification req,
                           FilterableResponseSpecification res,
                           FilterContext ctx) {
        String id = CURRENT.get();
        if (id != null) {
            req.header("X-Correlation-Id", id);
        }
        return ctx.next(req, res);
    }
}
