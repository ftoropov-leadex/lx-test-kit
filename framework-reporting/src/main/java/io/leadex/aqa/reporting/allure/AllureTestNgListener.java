package io.leadex.aqa.reporting.allure;

import io.leadex.aqa.config.EnvResolver;
import io.leadex.aqa.testsupport.testdata.DataRow;
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

    // Thread-local bridge to the Allure bus: this listener runs on the TestNG bus, where the typed
    // DataRow is available — but AllureTestNg starts the test case after our hooks run, so the name
    // is applied inside AllureLifecycle.stopTestCase by AllureLogAttachListener.beforeTestStop.
    private static final ThreadLocal<String> DISPLAY_NAME = new ThreadLocal<>();

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
        String caseName = caseNameOf(result);
        if (caseName == null) {
            // no stale name may leak into this test's beforeTestStop
            DISPLAY_NAME.remove();
        } else {
            DISPLAY_NAME.set(displayNameOf(result, caseName));
        }
    }

    @Override
    public void onTestFailure(ITestResult result) {
        if (result.getThrowable() != null) {
            Allure.addAttachment("Failure stacktrace", "text/plain",
                stackTraceOf(result.getThrowable()), ".txt");
        }
    }

    /**
     * Names a data-driven invocation after its {@code caseName} column so report rows are
     * distinguishable — without it every row of a method renders as raw DataRow JSON.
     * No {@code caseName} (or a non-data-driven test) leaves the default name untouched.
     */
    private static String caseNameOf(ITestResult result) {
        Object[] parameters = result.getParameters();
        if (parameters == null) {
            return null;
        }
        for (Object parameter : parameters) {
            if (parameter instanceof DataRow row && row.caseName() != null) {
                return row.caseName();
            }
        }
        return null;
    }

    /**
     * {@code methodName — caseName}. Neither half identifies a row on its own: the method repeats
     * across the rows of one method, and the caseName repeats across methods that share one dataset
     * row (observed in a consumer run — five different tests all rendered as {@code customer-search}).
     * The pair is unique in both configurations.
     */
    private static String displayNameOf(ITestResult result, String caseName) {
        String methodName = result.getMethod().getMethodName();
        return (methodName == null || methodName.isBlank()) ? caseName : methodName + " — " + caseName;
    }

    /**
     * Hands the current test's display name to {@link AllureLogAttachListener}, which applies
     * it while the test case is still current. Returns {@code null} when the test has none.
     */
    static String drainDisplayName() {
        String displayName = DISPLAY_NAME.get();
        DISPLAY_NAME.remove();
        return displayName;
    }

    // Writes environment.properties and (for local runs) executor.json to the Allure results directory
    // after the suite finishes — populates the Allure Environment and Executors widgets.
    @Override
    public void onFinish(ISuite suite) {
        // Default to the directory Allure itself writes results to, so the widget inputs
        // don't land in a stray sibling dir. Precedence: ALLURE_ENV_DIR → allure.results.directory → allure-results
        String outputDir = EnvResolver.string("ALLURE_ENV_DIR",
            System.getProperty("allure.results.directory", "allure-results"));
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

    private String stackTraceOf(Throwable throwable) {
        StringWriter writer = new StringWriter();
        throwable.printStackTrace(new PrintWriter(writer));
        return writer.toString();
    }
}
