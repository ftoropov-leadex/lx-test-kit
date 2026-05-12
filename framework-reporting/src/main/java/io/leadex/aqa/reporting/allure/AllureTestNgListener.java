package io.leadex.aqa.reporting.allure;

import io.leadex.aqa.config.EnvResolver;
import io.leadex.aqa.testsupport.retry.FrameworkRetryAnalyzer;
import io.qameta.allure.Allure;
import io.qameta.allure.testng.AllureTestNg;
import io.restassured.RestAssured;
import org.testng.ITestContext;
import org.testng.ITestResult;
import org.testng.ISuite;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;

public final class AllureTestNgListener extends AllureTestNg {

    private static volatile boolean filtersRegistered;

    @Override
    public void onStart(ITestContext context) {
        super.onStart(context);
        if (!filtersRegistered) {
            TestLogAppender.install();
            RestAssured.filters(new AllureHttpFilter());
            filtersRegistered = true;
        }
    }

    @Override
    public void onTestStart(ITestResult result) {
        super.onTestStart(result);
        TestLogAppender.startCapture();
    }

    @Override
    public void onTestSuccess(ITestResult result) {
        attachTestLog();
        super.onTestSuccess(result);
    }

    @Override
    public void onTestFailure(ITestResult result) {
        attachTestLog();
        attachRetryMetadata(result);
        if (result.getThrowable() != null) {
            Allure.addAttachment("Failure stacktrace", "text/plain",
                stackTraceOf(result.getThrowable()), ".txt");
        }
        super.onTestFailure(result);
    }

    @Override
    public void onTestSkipped(ITestResult result) {
        TestLogAppender.stopAndDrain();
        super.onTestSkipped(result);
    }

    // Writes environment.properties and (for local runs) executor.json to the Allure results directory
    // after the suite finishes — populates the Allure Environment and Executors widgets.
    @Override
    public void onFinish(ISuite suite) {
        super.onFinish(suite);
        String outputDir = EnvResolver.string("ALLURE_ENV_DIR", "allure-results");
        try {
            String env = EnvResolver.string("FRAMEWORK_ENV", "dev");
            Properties props = new Properties();
            props.setProperty("Environment", env);
            Files.createDirectories(Paths.get(outputDir));
            try (FileWriter writer = new FileWriter(outputDir + "/environment.properties")) {
                props.store(writer, null);
            }
        } catch (Exception e) {
            // silent — environment.properties is non-critical
        }
        if (EnvResolver.string("CI", "").isBlank()) {
            try {
                Files.createDirectories(Paths.get(outputDir));
                Files.writeString(Paths.get(outputDir, "executor.json"),
                    "{\"name\":\"Local\",\"type\":\"manual\",\"buildName\":\"Local run\"}");
            } catch (Exception e) {
                // silent — executor.json is non-critical
            }
        }
    }

    private void attachTestLog() {
        String logs = TestLogAppender.stopAndDrain();
        if (!logs.isBlank()) {
            Allure.addAttachment("Test execution log", "text/plain", logs, ".log");
        }
    }

    private void attachRetryMetadata(ITestResult result) {
        Object retry = result.getAttribute(FrameworkRetryAnalyzer.RETRY_ATTEMPT_ATTRIBUTE);
        Object reason = result.getAttribute(FrameworkRetryAnalyzer.RETRY_REASON_ATTRIBUTE);
        if (retry != null || reason != null) {
            Allure.addAttachment("Retry metadata",
                "retryAttempt=" + retry + ", retryReason=" + reason);
        }
    }

    private String stackTraceOf(Throwable throwable) {
        StringWriter writer = new StringWriter();
        throwable.printStackTrace(new PrintWriter(writer));
        return writer.toString();
    }
}
