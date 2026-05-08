package com.apiframework.reporting.allure;

import io.qameta.allure.listener.StepLifecycleListener;
import io.qameta.allure.model.StepResult;

import java.util.List;

/**
 * Allure {@link StepLifecycleListener} that cleans up step noise produced by
 * {@link io.qameta.allure.assertj.AllureAspectJ}.
 *
 * <h3>Truncation</h3>
 * <p>{@link io.qameta.allure.assertj.AllureAspectJ} names steps by calling
 * {@code ObjectUtils.toString()} on the value passed to {@code assertThat()}.
 * For arrays/collections this is a full {@code Arrays.toString()}
 * dump — an unreadable wall of text.  Names longer than {@value #MAX_NAME_LENGTH} characters
 * are truncated with {@code …}.
 *
 * <h3>Noise filtering</h3>
 * <p>In {@link #beforeStepStop}, two categories of child steps are removed before the step is
 * written to the result file:
 * <ol>
 *   <li><b>Exact-name duplicates</b> — AspectJ intercepts assertion chain methods (e.g.
 *       {@code isEqualTo}) both at the user-code callsite and again inside AssertJ's own
 *       implementation, producing {@code isEqualTo 'X' → isEqualTo 'X'}.  Children whose name
 *       equals the parent name are stripped.</li>
 *   <li><b>Internal library steps</b> — steps whose name starts with a prefix in
 *       {@link #NOISE_PREFIXES} are internal setup calls from third-party libraries (e.g.
 *       json-unit's {@code setCustomRepresentation}, {@code usingComparator}).  They add no
 *       value to a human reader of the report.</li>
 * </ol>
 *
 * <p>Registered via {@code META-INF/services/io.qameta.allure.listener.StepLifecycleListener}
 * so Allure picks it up automatically through {@code ServiceLoader}.
 */
public final class StepNameTruncator implements StepLifecycleListener {

    static final int MAX_NAME_LENGTH = 120;
    private static final String ELLIPSIS = "…";

    /**
     * Step name prefixes that represent internal library implementation details and should
     * never appear in the report.  Populated from observed AllureAspectJ + json-unit-assertj
     * interception artefacts.
     */
    private static final List<String> NOISE_PREFIXES = List.of(
            "setCustomRepresentation ",
            "usingComparator "
    );

    @Override
    public void beforeStepStart(StepResult result) {
        String name = result.getName();
        if (name != null && name.length() > MAX_NAME_LENGTH) {
            result.setName(name.substring(0, MAX_NAME_LENGTH) + ELLIPSIS);
        }
    }

    @Override
    public void beforeStepStop(StepResult result) {
        if (result.getSteps().isEmpty()) {
            return;
        }
        String name = result.getName();
        result.getSteps().removeIf(child -> {
            String childName = child.getName();
            if (childName == null) return false;
            // exact-name duplicate (AspectJ double-interception)
            if (childName.equals(name)) return true;
            // internal library implementation steps
            return NOISE_PREFIXES.stream().anyMatch(childName::startsWith);
        });
    }
}
