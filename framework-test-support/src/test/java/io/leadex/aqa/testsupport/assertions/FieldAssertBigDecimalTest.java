package io.leadex.aqa.testsupport.assertions;

import com.fasterxml.jackson.databind.node.DecimalNode;
import com.fasterxml.jackson.databind.node.DoubleNode;
import org.testng.annotations.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@code hasValue(BigDecimal)} — scale-insensitive {@code compareTo} against any numeric
 * node (task A7). {@code 7.745} matches a response carrying {@code 7.7450}; a mismatch
 * fails with exact decimals on both sides, never the misleading
 * {@code expected <7.745> but was <7.745>} produced by the old {@code equals} path.
 */
public class FieldAssertBigDecimalTest {

    @Test
    public void bigDecimalMatchesNumericNodeScaleInsensitively() {
        assertThatCode(() -> new FieldAssert(new DecimalNode(new BigDecimal("7.7450")), "amount")
                .hasValue(new BigDecimal("7.745")))
                .doesNotThrowAnyException();
        assertThatCode(() -> new FieldAssert(new DoubleNode(7.745), "amount")
                .hasValue(new BigDecimal("7.745")))
                .doesNotThrowAnyException();
    }

    @Test
    public void mismatchFailsWithExactDecimalsOnBothSides() {
        assertThatThrownBy(() -> new FieldAssert(new DecimalNode(new BigDecimal("7.745")), "amount")
                .hasValue(new BigDecimal("7.746")))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("7.746")
                .hasMessageContaining("7.745");
    }
}
