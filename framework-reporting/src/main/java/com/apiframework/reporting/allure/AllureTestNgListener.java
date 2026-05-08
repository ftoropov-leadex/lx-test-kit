package com.apiframework.reporting.allure;

import com.apiframework.config.ConfigResolver;
import com.apiframework.config.EnvResolver;
import com.apiframework.testsupport.retry.FrameworkRetryAnalyzer;
import io.qameta.allure.Allure;
import io.restassured.RestAssured;
import org.testng.ITestContext;
import org.testng.ITestListener;
import org.testng.ITestResult;
import org.testng.ISuite;
import org.testng.ISuiteListener;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;

public final class AllureTestNgListener implements ITestListener, ISuiteListener {

    private static volatile boolean filtersRegistered;

    @Override
    public void onStart(ITestContext context) {
        if (!filtersRegistered) {
            TestLogAppender.install();
            RestAssured.filters(new AllureHttpFilter());
            filtersRegistered = true;
        }
    }

    @Override
    public void onTestStart(ITestResult result) {
        TestLogAppender.startCapture();
    }

    @Override
    public void onTestSuccess(ITestResult result) {
        attachTestLog();
    }

    @Override
    public void onTestFailure(ITestResult result) {
        attachTestLog();
        attachRetryMetadata(result);
        if (result.getThrowable() != null) {
            Allure.addAttachment("Failure stacktrace", "text/plain",
                stackTraceOf(result.getThrowable()), ".txt");
        }
    }

    @Override
    public void onTestSkipped(ITestResult result) {
        TestLogAppender.stopAndDrain();
    }

    // Writes environment.properties and (for local runs) executor.json to the Allure results directory
    // after the suite finishes — populates the Allure Environment and Executors widgets.
    @Override
    public void onFinish(ISuite suite) {
        String outputDir = EnvResolver.string("ALLURE_ENV_DIR", "allure-results");
        try {
            String env = EnvResolver.string("FRAMEWORK_ENV", "dev");;
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
