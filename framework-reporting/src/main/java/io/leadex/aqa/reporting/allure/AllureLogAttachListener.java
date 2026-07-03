package io.leadex.aqa.reporting.allure;

import io.leadex.aqa.testsupport.retry.FrameworkRetryAnalyzer;
import io.qameta.allure.Allure;
import io.qameta.allure.listener.TestLifecycleListener;
import io.qameta.allure.model.TestResult;

/**
 * Attaches the captured test-execution log to the Allure result.
 *
 * <p>Runs on Allure's lifecycle bus rather than TestNG's. {@code beforeTestStop} fires inside
 * {@code AllureLifecycle.stopTestCase}, before {@code threadContext.clear()} and before the result
 * is written — so {@link Allure#addAttachment} binds to the still-current test case. A peer
 * {@code ITestListener} (e.g. our {@link AllureTestNgListener}) cannot guarantee this: under SPI
 * registration its {@code onTestSuccess}/{@code onTestFailure} may run after {@code AllureTestNg}
 * has already stopped and written the case, leaving the log written to disk but unreferenced.
 *
 * <p>Discovered via {@code META-INF/services/io.qameta.allure.listener.TestLifecycleListener};
 * requires a public no-arg constructor for {@link java.util.ServiceLoader}.
 */
public final class AllureLogAttachListener implements TestLifecycleListener {

    @Override
    public void beforeTestStop(TestResult result) {
        String logs = TestLogAppender.stopAndDrain();
        if (!logs.isBlank()) {
            Allure.addAttachment("Test execution log", "text/plain", logs, ".log");
        }
        String retryMetadata = FrameworkRetryAnalyzer.drainMetadata();
        if (retryMetadata != null) {
            Allure.addAttachment("Retry metadata", retryMetadata);
        }
    }
}
