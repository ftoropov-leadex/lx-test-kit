package io.leadex.aqa.reporting.allure;

import io.leadex.aqa.config.EnvResolver;
import io.qameta.allure.Allure;
import io.restassured.filter.Filter;
import io.restassured.filter.FilterContext;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.FilterableResponseSpecification;

import java.net.URI;
import java.util.Arrays;
import java.util.List;

public final class AllureHttpFilter implements Filter {

    private static final int MAX_BODY_LENGTH = 10_240;

    /*
     * Framework plumbing — polling and session establishment — is not business evidence: a poll loop
     * turns one await into N identical steps with 2N attachments, while the per-attempt narrative is
     * already carried by the Test execution log attachment (the query, each attempt's timestamp, the
     * result counts). Calls to the paths below still execute; they just do not become report steps.
     *
     * Patterns match the request path: '*' is exactly one path segment (never a '/'), '**' is the
     * whole remaining tail. The default list hides the Splunk session login, the export polls and the
     * dispatchState polls, while leaving POST /services/search/jobs (job creation) and
     * GET /services/search/jobs/{sid}/results (the rows) visible.
     *
     * FRAMEWORK_REPORT_HIDE_POLLS=false restores full verbosity. It is a boolean rather than a blank
     * value because EnvResolver treats a blank value as unset and would fall back to the default.
     */
    private static final boolean HIDE_POLLS = EnvResolver.bool("FRAMEWORK_REPORT_HIDE_POLLS", true);

    private static final List<String> POLL_PATHS = parsePatterns(EnvResolver.string(
        "FRAMEWORK_REPORT_POLL_PATHS",
        "/services/auth/login,/services/search/jobs/export,/services/search/jobs/*"));

    @Override
    public Response filter(FilterableRequestSpecification req,
                           FilterableResponseSpecification resSpec,
                           FilterContext ctx) {
        String method = String.valueOf(req.getMethod());
        String path = URI.create(req.getURI()).getPath();

        if (HIDE_POLLS && matchesAny(POLL_PATHS, path)) {
            // The call still happens — only the reporting of it is skipped.
            return ctx.next(req, resSpec);
        }

        String stepName = method + " " + path;

        return Allure.step(stepName, () -> {
            Allure.addAttachment("Request", "text/plain", formatRequest(req), ".txt");
            Response response = ctx.next(req, resSpec);
            Allure.addAttachment("Response", "text/plain", formatResponse(response), ".txt");
            return response;
        });
    }

    private static List<String> parsePatterns(String csv) {
        return Arrays.stream(csv.split(","))
            .map(String::trim)
            .filter(pattern -> !pattern.isEmpty())
            .toList();
    }

    private static boolean matchesAny(List<String> patterns, String path) {
        for (String pattern : patterns) {
            if (matches(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matches(String pattern, String path) {
        String[] patternSegments = pattern.split("/", -1);
        String[] pathSegments = path.split("/", -1);
        int i = 0;
        for (; i < patternSegments.length; i++) {
            if (patternSegments[i].equals("**")) {
                return true;
            }
            if (i >= pathSegments.length) {
                return false;
            }
            if (!patternSegments[i].equals("*") && !patternSegments[i].equals(pathSegments[i])) {
                return false;
            }
        }
        return i == pathSegments.length;
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
