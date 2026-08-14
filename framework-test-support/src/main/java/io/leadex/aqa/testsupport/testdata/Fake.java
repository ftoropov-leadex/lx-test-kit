package io.leadex.aqa.testsupport.testdata;

import net.datafaker.Faker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.Random;

/**
 * Fake-data generation for tests, backed by one framework-wide, seeded
 * <a href="https://www.datafaker.net/">DataFaker</a> instance.
 *
 * <p>Two consumption paths off the same instance:
 * <ul>
 *   <li><b>Test-class direct</b> — the pattern delegates ({@link #numerify},
 *       {@link #letterify}, {@link #regexify}, {@link #bothify}), the typed helpers
 *       ({@link #integer}, {@link #decimal}, {@link #bool}), or the full DataFaker
 *       catalog via {@link #faker()} (intentional escape hatch — names, phones,
 *       addresses, ...).</li>
 *   <li><b>Dataset placeholders</b> — DataFaker {@code #{...}} expressions inside JSON
 *       dataset files, resolved by {@link ExtractTestData#from(String)} via
 *       {@link #expression(String)}.</li>
 * </ul>
 *
 * <p>Seed contract: the seed is drawn once per run (random), or taken from the
 * {@code FRAMEWORK_FAKER_SEED} env var to reproduce a specific run. The same seed
 * yields the same sequence of values, in call order. The seed is logged at init
 * ({@code faker seed=<value>}) — the reproduction handle for any faker-influenced
 * failure. Dataset placeholders resolve once at dataset load, so retries and report
 * entries show stable values.
 *
 * <p>Identity boundary: faker generates <i>content</i> data (names, phones, amounts,
 * negative-test garbage). It never replaces server-minted identifiers returned by
 * precondition API calls.
 */
public final class Fake {

    private static final Logger log = LoggerFactory.getLogger(Fake.class);

    private static volatile Faker faker;

    private Fake() {}

    /** Delegates to {@link Faker#numerify(String)} — {@code #} becomes a random digit. */
    public static String numerify(String pattern) {
        return faker().numerify(pattern);
    }

    /** Delegates to {@link Faker#letterify(String)} — {@code ?} becomes a random letter. */
    public static String letterify(String pattern) {
        return faker().letterify(pattern);
    }

    /** Delegates to {@link Faker#regexify(String)} — generates a string matching the regex. */
    public static String regexify(String pattern) {
        return faker().regexify(pattern);
    }

    /** Delegates to {@link Faker#bothify(String)} — combines {@link #numerify} and {@link #letterify}. */
    public static String bothify(String pattern) {
        return faker().bothify(pattern);
    }

    /**
     * Evaluates a native DataFaker expression such as {@code #{numerify '##########'}}.
     * The dataset-placeholder form; also usable test-side.
     */
    public static String expression(String expression) {
        return faker().expression(expression);
    }

    /** Random int between {@code min} and {@code max}, both inclusive. */
    public static int integer(int min, int max) {
        // long math so min=Integer.MIN_VALUE / max=Integer.MAX_VALUE stay expressible
        return (int) faker().random().nextLong(min, (long) max + 1);
    }

    /**
     * Random decimal with the whole part between {@code min} and {@code max} (both
     * inclusive) and exactly {@code scale} fraction digits — the same exact-scale
     * contract as dataset decimals ({@code 100.00} stays {@code 100.00}).
     */
    public static BigDecimal decimal(long min, long max, int scale) {
        long whole = faker().random().nextLong(min, max + 1);
        long fraction = scale <= 0
            ? 0
            : faker().random().nextLong(BigDecimal.TEN.pow(scale).longValueExact());
        return BigDecimal.valueOf(whole).add(BigDecimal.valueOf(fraction, scale));
    }

    /** Random boolean. */
    public static boolean bool() {
        return faker().random().nextBoolean();
    }

    /**
     * Escape hatch: the shared {@link Faker} instance, for the full DataFaker catalog
     * ({@code Fake.faker().name().fullName()} and friends). The library type leaking
     * into consumer code is an accepted trade-off — the dependency is {@code api}-scoped
     * deliberately and version bumps are owned centrally.
     */
    public static Faker faker() {
        Faker instance = faker;
        if (instance == null) {
            synchronized (Fake.class) {
                instance = faker;
                if (instance == null) {
                    instance = newFaker();
                    faker = instance;
                }
            }
        }
        return instance;
    }

    private static Faker newFaker() {
        long seed = seedFromEnv();
        log.info("faker seed={}", seed);
        return new Faker(new Random(seed));
    }

    private static long seedFromEnv() {
        String value = System.getenv("FRAMEWORK_FAKER_SEED");
        if (value == null || value.isBlank()) {
            return new Random().nextLong();
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Invalid long for FRAMEWORK_FAKER_SEED: " + value);
        }
    }
}
