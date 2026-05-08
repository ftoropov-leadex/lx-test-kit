package com.apiframework.reporting.allure;

import io.qameta.allure.Allure;
import io.restassured.filter.Filter;
import io.restassured.filter.FilterContext;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.FilterableResponseSpecification;

import java.net.URI;

public final class AllureHttpFilter implements Filter {

    private static final int MAX_BODY_LENGTH = 10_240;

    @Override
    public Response filter(FilterableRequestSpecification req,
                           FilterableResponseSpecification resSpec,
                           FilterContext ctx) {
        String method = String.valueOf(req.getMethod());
        String path = URI.create(req.getURI()).getPath();
        String stepName = method + " " + path;

        return Allure.step(stepName, () -> {
            Response response = ctx.next(req, resSpec);
            Allure.addAttachment("Request", "text/plain", formatRequest(req), ".txt");
            Allure.addAttachment("Response", "text/plain", formatResponse(response), ".txt");
            return response;
        });
    }

    private String formatRequest(FilterableRequestSpecification req) {
        var sb = new StringBuilder();
        sb.append(req.getMethod()).append(" ").append(req.getURI()).append("\n");
        req.getHeaders().forEach(h -> sb.append(h.getName()).append(": ").append(h.getValue()).append("\n"));
        if (req.getBody() != null) {
            sb.append("\n").append(truncate(req.getBody().toString()));
        }
        return sb.toString();
    }

    private String formatResponse(Response response) {
        var sb = new StringBuilder();
        sb.append("Status: ").append(response.getStatusLine()).append("\n");
        response.getHeaders().forEach(h -> sb.append(h.getName()).append(": ").append(h.getValue()).append("\n"));
        String body = response.getBody().asString();
        if (body != null && !body.isEmpty()) {
            sb.append("\n").append(truncate(body));
        }
        return sb.toString();
    }

    private String truncate(String text) {
        return text.length() > MAX_BODY_LENGTH
            ? text.substring(0, MAX_BODY_LENGTH) + "\n... [truncated]"
            : text;
    }
}
