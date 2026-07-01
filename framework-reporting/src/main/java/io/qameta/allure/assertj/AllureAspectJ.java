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
     * Per-call frame stack: stepStart pushes one Boolean for every intercepted assertion call —
     * TRUE when the call was skipped (no step opened), FALSE when a real step was opened. stepStop
     * and stepFailed pop the matching frame and act on it. AspectJ guarantees exactly one @Before
     * and one @AfterReturning/@AfterThrowing per join point in strict LIFO order, so the stack stays
     * balanced regardless of how skipped and non-skipped calls nest — e.g. a skipped first() whose
     * body invokes a non-skipped isNotEmpty(). A single depth counter could not represent this: an
     * inner non-skipped call would clobber the outer skipped call's pending state, causing the outer
     * stepStop to close a step that was never opened (logged as "no step running" / test-uuid
     * "step not found").
     */
    private static final ThreadLocal<java.util.Deque<Boolean>> callStack =
            ThreadLocal.withInitial(java.util.ArrayDeque::new);

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

    /**
     * Matches all public assertion methods on AbstractAssert subclasses, excluding proxy setup
     * and jsonunit's own fluent API.
     *
     * <p>{@code net.javacrumbs.jsonunit.assertj.ConfigurableJsonAssert} extends AssertJ's
     * {@code AbstractAssert}, so without the {@code !within} clause the aspect intercepts
     * jsonunit's internal {@code when()}/{@code withConfiguration()}/{@code isEqualTo()} calls
     * made by {@code SnapshotContractValidator}, leaking them as ugly sub-steps under
     * {@code matches snapshot} (including a raw {@code Lambda@...} toString). jsonunit assertions
     * are framework-internal, never user-authored, so excluding the package keeps
     * {@code matches snapshot} a clean leaf. No user-facing DSL routes through jsonunit.
     */
    @Pointcut("execution(public * org.assertj.core.api.AbstractAssert+.*(..)) "
            + "&& !proxyMethod() && !within(net.javacrumbs.jsonunit..*)")
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

        //Noise filter cuts:
        if (typeName.equals("ArrayNode")
                 || typeName.equals("ApiResponse") // assertThat [ApiResponse]
                 || typeName.equals("ObjectNode") //  assert in json validation step
                 || typeName.equals("TextNode")   //  assert in json validation step (array validation)
                 || typeName.equals("IntNode")
                 || typeName.equals("LongNode")
                 || typeName.equals("DoubleNode")
                 || typeName.equals("FloatNode")
                 || typeName.equals("ShortNode")
                 || typeName.equals("BooleanNode")
                 || typeName.equals("BigIntegerNode")
                 || typeName.equals("DecimalNode")
                 || typeName.equals("NullNode")
                 || typeName.equals("MissingNode")
                 || typeName.equals("POJONode")
                 || typeName.equals("BinaryNode")
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

        if (shouldSkip(methodName, args)) {
            callStack.get().push(Boolean.TRUE);   // skipped — no step opened
            return;
        }

        callStack.get().push(Boolean.FALSE);      // real step opened below
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
        if (popSkipped()) {
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
        if (popSkipped()) {
            return;
        }
        getLifecycle().updateStep(s -> s.setStatus(Status.PASSED));
        getLifecycle().stopStep();
    }

    /*
     * Pops the frame pushed by the matching stepStart. Returns true when that call was skipped
     * (no step to close). An empty stack means stepStart never ran for this join point — treat as
     * skipped (return true) so we never close a step we did not open.
     */
    private static boolean popSkipped() {
        final java.util.Deque<Boolean> stack = callStack.get();
        return stack.isEmpty() || stack.pop();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /*
     * Signature-aware skip:
     *  - isNotNull / assertThat are always navigation/internal noise → always skipped.
     *  - body / first / at are skipped ONLY when they carry no trailing Consumer arg. This
     *    keeps Splunk's no-arg first() step-less (it must never blanket-removed from the skip
     *    list) while letting the new lambda-scoped grouping overloads emit a real, balanced
     *    step whose field/contract children nest inside it.
     */
    private boolean shouldSkip(final String methodName, final Object[] args) {
        if (methodName.equals("isNotNull") || methodName.equals("assertThat")) {
            return true;
        }
        final boolean hasConsumer = args.length > 0
                && args[args.length - 1] instanceof java.util.function.Consumer;
        if (methodName.equals("body") || methodName.equals("first") || methodName.equals("at")) {
            return !hasConsumer;
        }
        return false;
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
            // Lambda-scoped grouping methods: trailing Consumer arg ignored, name from args[0].
            case "field" -> "field '" + args[0] + "'";
            case "body"  -> "body";
            case "first" -> "first";
            case "at"    -> "at[" + args[0] + "]";
            default           -> null;
        };
    }

    private static String arrayToString(final Object[] args) {
        return Stream.of(args)
                .map(ObjectUtils::toString)
                .collect(Collectors.joining(", "));
    }
}
