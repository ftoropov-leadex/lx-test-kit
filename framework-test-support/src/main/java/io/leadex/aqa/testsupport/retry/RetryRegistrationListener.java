package io.leadex.aqa.testsupport.retry;

import org.testng.ITestListener;
import org.testng.ITestResult;

public final class RetryRegistrationListener implements ITestListener {
    @Override
    public void onTestStart(ITestResult result) {
        if (result.getMethod().getRetryAnalyzer(result) == null) {   // preserve explicit @Test(retryAnalyzer=...)
            result.getMethod().setRetryAnalyzerClass(FrameworkRetryAnalyzer.class);
        }
    }
}
