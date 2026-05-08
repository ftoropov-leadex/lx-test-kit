package com.apiframework.reporting.allure;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.layout.PatternLayout;

public final class TestLogAppender extends AbstractAppender {

    private static final ThreadLocal<StringBuilder> BUFFER = ThreadLocal.withInitial(StringBuilder::new);
    private static volatile boolean installed;

    private TestLogAppender() {
        super("TestLogCapture", null,
              PatternLayout.newBuilder()
                  .withPattern("%d{HH:mm:ss.SSS} %-5level %c{1} - %msg%n")
                  .build(),
              true, Property.EMPTY_ARRAY);
    }

    @Override
    public void append(LogEvent event) {
        BUFFER.get().append(getLayout().toSerializable(event));
    }

    public static void startCapture() {
        BUFFER.get().setLength(0);
    }

    public static String stopAndDrain() {
        String logs = BUFFER.get().toString();
        BUFFER.remove();
        return logs;
    }

    public static void install() {
        if (installed) return;
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        Configuration config = ctx.getConfiguration();
        TestLogAppender appender = new TestLogAppender();
        appender.start();
        config.addAppender(appender);
        config.getRootLogger().addAppender(appender, Level.DEBUG, null);
        ctx.updateLoggers();
        installed = true;
    }
}
