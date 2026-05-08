package io.qameta.allure.assertj;

import io.qameta.allure.Allure;
import io.qameta.allure.AllureLifecycle;
import io.qameta.allure.model.Status;
import io.qameta.allure.model.StatusDetails;
import io.qameta.allure.model.StepResult;
import io.qameta.allure.util.ObjectUtils;
import io.qameta.allure.util.ResultsUtils;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Vendored copy of {@code io.qameta.allure:allure-assertj:2.33.0} AllureAspectJ.
 *
 * <p>Reason: the stock allure-assertj names the {@code assertThat} Allure step by calling
 * {@code ObjectUtils.toString(actual)} on the value under test, producing unreadable step names
 * like {@code assertThat 'ApiResponse[statusCode=200, headers=..., rawBody=...]'}. By vendoring
 * the aspect here we can apply the single naming fix below without forking the full library.
 *
 * <p>Changes vs. the original 2.33.0 source:
 * <ul>
 *   <li>{@link #logAssertCreation} uses {@code actual.getClass().getSimpleName()} instead of
 *       {@code ObjectUtils.toString(actual)}, producing {@code assertThat [ApiResponse]} instead
 *       of the full {@code toString()} dump.</li>
 *   <li>{@link #stepStart} skips navigation-only methods ({@code body}, {@code first}, {@code at},
 *       etc.) and applies compact human-readable names for common assertion methods.</li>
 * </ul>
 */
@Aspect
public class AllureAspectJ {

    private static final Logger LOGGER = LoggerFactory.getLogger(AllureAspectJ.class);

    /*
     * InheritableThreadLocal so that child threads spawned inside a test inherit the same
     * lifecycle instance — matching the original allure-assertj behavior.
     */
    private static final InheritableThreadLocal<AllureLifecycle> lifecycle =
            new InheritableThreadLocal<>() {
                @Override
                protected AllureLifecycle initialValue() {
                    return Allure.getLifecycle();
                }
            };

    /*
     * Reentrant depth counter: incremented by stepStart when a method is in the skip list,
     * decremented by stepStop/stepFailed. Using a counter (not a boolean) correctly handles
     * nested skipped calls — e.g. body() calling isNotNull() internally, where both are in
     * the skip list. A boolean would be cleared by the inner call's stepStop, causing the
     * outer call's stepStop to proceed and attempt to close a step that was never opened.
     */
    private static final ThreadLocal<Integer> skipDepth = new ThreadLocal<>();

    private static int getSkipDepth() {
        final Integer v = skipDepth.get();
        return v == null ? 0 : v;
    }

    public static AllureLifecycle getLifecycle() {
        return lifecycle.get();
    }

    // -------------------------------------------------------------------------
    // Pointcuts (unchanged from original)
    // -------------------------------------------------------------------------

    /** Matches non-private AbstractAssert constructor executions (i.e. every assertThat() call). */
    @Pointcut("execution(!private org.assertj.core.api.AbstractAssert.new(..))")
    public void anyAssertCreation() {
    }

    /** Matches AssertJ proxy-setup methods — excluded from step tracking to reduce noise. */
    @Pointcut("execution(* org.assertj.core.api.AssertJProxySetup.*(..))")
    public void proxyMethod() {
    }

    /** Matches all public assertion methods on AbstractAssert subclasses, excluding proxy setup. */
    @Pointcut("execution(public * org.assertj.core.api.AbstractAssert+.*(..)) && !proxyMethod()")
    public void anyAssert() {
    }

    // -------------------------------------------------------------------------
    // Advice
    // -------------------------------------------------------------------------

    /**
     * Creates and immediately closes a step named {@code assertThat [ClassName]} each time an
     * AssertJ assertion object is constructed.
     *
     * <p><b>Changed vs. original:</b> The original used {@code ObjectUtils.toString(actual)} which
     * produces the full {@code toString()} of the object under test — noisy for domain models.
     * This version uses {@code actual.getClass().getSimpleName()} so the step reads
     * {@code assertThat [ApiResponse]} regardless of how verbose the model's toString() is.
     */
    @After("anyAssertCreation()")
    public void logAssertCreation(final JoinPoint joinPoint) {
        // Changed: class simple name instead of ObjectUtils.toString() to avoid noisy step names
        final String typeName = (joinPoint.getArgs().length > 0 && joinPoint.getArgs()[0] != null)
                ? joinPoint.getArgs()[0].getClass().getSimpleName()
                : "value";

        //Noise filter#2 cuts:
        if (typeName.equals("ArrayNode")
                 || typeName.equals("ApiResponse") // assertThat [ApiResponse]
                 || typeName.equals("ObjectNode") //  assert in json validation step
                 || typeName.equals("TextNode")   //  assert in json validation step (array validation)
                 || typeName.equals("Jackson2Node")) return; // assert in snapshot validation step

        final String name = String.format("assertThat [%s]", typeName);
        final String uuid = UUID.randomUUID().toString();
        final StepResult step = new StepResult()
                .setName(name)
                .setStatus(Status.PASSED);

        getLifecycle().startStep(uuid, step);
        getLifecycle().stopStep(uuid);
    }

    /** Opens an Allure step for each assertion method call, skipping navigation-only methods. */
    @Before("anyAssert()")
    public void stepStart(final JoinPoint joinPoint) {
        final MethodSignature methodSignature = (MethodSignature) joinPoint.getSignature();
        final String methodName = methodSignature.getName();
        final Object[] args = joinPoint.getArgs();

        if (shouldSkip(methodName)) {
            skipDepth.set(getSkipDepth() + 1);
            return;
        }

        // Reset depth counter — clears any stale state left by a prior incomplete skip cycle
        skipDepth.remove();

        final String uuid = UUID.randomUUID().toString();
        final String pretty = prettify(methodName, args);
        final String name = pretty != null
                ? pretty
                : (args.length > 0
                        ? String.format("%s '%s'", methodName, arrayToString(args))
                        : methodName);

        final StepResult step = new StepResult()
                .setName(name)
                .setStatus(Status.PASSED);
        getLifecycle().startStep(uuid, step);
    }

    /** Marks the current step as failed/broken when an assertion throws. */
    @AfterThrowing(pointcut = "anyAssert()", throwing = "e")
    public void stepFailed(final Throwable e) {
        final int depth = getSkipDepth();
        if (depth > 0) {
            final int next = depth - 1;
            if (next == 0) skipDepth.remove(); else skipDepth.set(next);
            return;
        }
        getLifecycle().updateStep(s -> {
            s.setStatus(ResultsUtils.getStatus(e).orElse(Status.BROKEN));
            s.setStatusDetails(ResultsUtils.getStatusDetails(e).orElse(new StatusDetails()));
        });
        getLifecycle().stopStep();
    }

    /** Marks the current step as passed and closes it after a successful assertion. */
    @AfterReturning("anyAssert()")
    public void stepStop() {
        final int depth = getSkipDepth();
        if (depth > 0) {
            final int next = depth - 1;
            if (next == 0) skipDepth.remove(); else skipDepth.set(next);
            return;
        }
        getLifecycle().updateStep(s -> s.setStatus(Status.PASSED));
        getLifecycle().stopStep();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private boolean shouldSkip(final String methodName) {
        return methodName.equals("isNotNull")
                || methodName.equals("assertThat")
                || methodName.equals("body")
                || methodName.equals("first")
                || methodName.equals("at");
    }

    private String prettify(final String methodName, final Object[] args) {
        return switch (methodName) {
            case "isEqualTo" -> {                           // cut's JSON in report step to 80smb
                if (args.length == 0 || args[0] == null) {  // JSON null-protection
                    yield "equals";
                }
                String value = String.valueOf(args[0]);
                yield value.length() > 80                               // number off smb to set/change
                        ? "equals '" + value.substring(0, 80) + "...'"  // number off smb to set/change
                        : "equals '" + value + "'";
            }
            case "hasStatus" -> "status " + args[0];
            case "isNotBlank" -> "not blank";
            case "isNotEmpty" -> "not empty";
            case "matchesSchema" -> "matches schema";
            case "matchesSnapshot" -> "matches snapshot";
            default           -> null;
        };
    }

    private static String arrayToString(final Object[] args) {
        return Stream.of(args)
                .map(ObjectUtils::toString)
                .collect(Collectors.joining(", "));
    }
}
