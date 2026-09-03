package io.leadex.aqa.http;

import io.restassured.filter.FilterContext;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.FilterableResponseSpecification;
import org.testng.annotations.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the correlation-id contract: per-thread lifecycle without cross-thread bleed, and
 * injection of {@code X-Correlation-Id} into every request exactly when an ID is set.
 * {@code filter()} is exercised through JDK dynamic proxies — no socket involved.
 */
public class CorrelationIdFilterTest {

    @Test
    public void setThenCurrentIdThenClear() {
        CorrelationIdFilter.set("trace-1");
        try {
            assertThat(CorrelationIdFilter.currentId()).isEqualTo("trace-1");
        } finally {
            CorrelationIdFilter.clear();
        }
        assertThat(CorrelationIdFilter.currentId()).isNull();
    }

    @Test
    public void threadLocalIsolation() throws Exception {
        var ready = new CountDownLatch(2);
        var go = new CountDownLatch(1);
        var seenByA = new AtomicReference<String>();
        var seenByB = new AtomicReference<String>();

        Thread a = new Thread(() -> {
            CorrelationIdFilter.set("thread-A");
            ready.countDown();
            try {
                go.await();
                seenByA.set(CorrelationIdFilter.currentId());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                CorrelationIdFilter.clear();
            }
        });
        Thread b = new Thread(() -> {
            CorrelationIdFilter.set("thread-B");
            ready.countDown();
            try {
                go.await();
                seenByB.set(CorrelationIdFilter.currentId());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                CorrelationIdFilter.clear();
            }
        });

        a.start();
        b.start();
        ready.await();
        go.countDown();
        a.join();
        b.join();

        assertThat(seenByA.get()).isEqualTo("thread-A");
        assertThat(seenByB.get()).isEqualTo("thread-B");
        assertThat(CorrelationIdFilter.currentId()).isNull();
    }

    @Test
    public void filterInjectsHeaderWhenIdSet() {
        CorrelationIdFilter.set("trace-9");
        try {
            var wire = wire();
            new CorrelationIdFilter().filter(wire.requestSpec(), wire.responseSpec(), wire.context());
            assertThat(wire.capturedHeaders()).containsExactly("X-Correlation-Id", "trace-9");
            assertThat(wire.nextCalled().get()).isTrue();
        } finally {
            CorrelationIdFilter.clear();
        }
    }

    @Test
    public void filterAddsNoHeaderWhenIdUnset() {
        CorrelationIdFilter.clear();
        var wire = wire();
        new CorrelationIdFilter().filter(wire.requestSpec(), wire.responseSpec(), wire.context());
        assertThat(wire.capturedHeaders()).isEmpty();
        assertThat(wire.nextCalled().get()).isTrue();
    }

    /** JDK dynamic proxies: capture {@code header()} on the request spec and {@code next()} on the context. */
    private static Wire wire() {
        List<String> captured = new ArrayList<>();
        AtomicBoolean nextCalled = new AtomicBoolean();
        ClassLoader loader = FilterContext.class.getClassLoader();

        FilterableRequestSpecification requestSpec =
                (FilterableRequestSpecification) Proxy.newProxyInstance(loader,
                        new Class<?>[]{FilterableRequestSpecification.class},
                        (proxy, method, args) -> {
                            if (method.getName().equals("header")) {
                                captured.add((String) args[0]);
                                captured.add(String.valueOf(args[1]));
                            }
                            return proxy;
                        });
        FilterableResponseSpecification responseSpec =
                (FilterableResponseSpecification) Proxy.newProxyInstance(loader,
                        new Class<?>[]{FilterableResponseSpecification.class},
                        (proxy, method, args) -> null);
        FilterContext context = (FilterContext) Proxy.newProxyInstance(loader,
                new Class<?>[]{FilterContext.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("next")) {
                        nextCalled.set(true);
                    }
                    return null;
                });
        return new Wire(requestSpec, responseSpec, context, captured, nextCalled);
    }

    private record Wire(FilterableRequestSpecification requestSpec,
                        FilterableResponseSpecification responseSpec,
                        FilterContext context,
                        List<String> capturedHeaders,
                        AtomicBoolean nextCalled) {
    }
}
