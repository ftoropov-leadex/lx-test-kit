package io.leadex.aqa.reporting.allure;

import io.leadex.aqa.testsupport.retry.FrameworkRetryAnalyzer;
import io.qameta.allure.Allure;
import io.qameta.allure.listener.TestLifecycleListener;
import io.qameta.allure.model.TestResult;

/**
 * Attaches the captured test-execution log to the Allure result, and names a data-driven
 * invocation {@code methodName — caseName}.
 *
 * <p>Runs on Allure's lifecycle bus rather than TestNG's. {@code beforeTestStop} fires inside
 * {@code AllureLifecycle.stopTestCase}, before {@code threadContext.clear()} and before the result
 * is written — so {@link Allure#addAttachment} binds to the still-current test case, and
 * {@code updateTestCase} can still rename it. A peer {@code ITestListener} (e.g. our
 * {@link AllureTestNgListener}) cannot guarantee this: under SPI registration its
 * {@code onTestStart} runs before the case exists, and its {@code onTestSuccess}/
 * {@code onTestFailure} may run after {@code AllureTestNg} has already stopped and written the
 * case — observed on a real run, not assumed. The name itself is produced on the
 * TestNG bus, where the typed {@code DataRow} lives, and carried over the ThreadLocal bridge in
 * {@link AllureTestNgListener#drainDisplayName()}.
 *
 * <p>Discovered via {@code META-INF/services/io.qameta.allure.listener.TestLifecycleListener};
 * requires a public no-arg constructor for {@link java.util.ServiceLoader}.
 */
public final class AllureLogAttachListener implements TestLifecycleListener {

    @Override
    public void beforeTestStop(TestResult result) {
        String displayName = AllureTestNgListener.drainDisplayName();
        if (displayName != null) {
            Allure.getLifecycle().updateTestCase(testCase -> testCase.setName(displayName));
        }
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
