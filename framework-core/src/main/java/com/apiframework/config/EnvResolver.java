package com.apiframework.config;

public final class EnvResolver {

    private EnvResolver() {}

    public static String string(String name, String def) {
        String v = System.getenv(name);
        return (v == null || v.isBlank()) ? def : v.trim();
    }

    public static String required(String name) {
        String v = System.getenv(name);
        if (v == null || v.isBlank()) {
            throw new IllegalStateException("Required env var missing: " + name);
        }
        return v.trim();
    }

    public static int integer(String name, int def) {
        String v = System.getenv(name);
        if (v == null || v.isBlank()) return def;
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Invalid int for " + name + ": " + v);
        }
    }

    public static boolean bool(String name, boolean def) {
        String v = System.getenv(name);
        if (v == null || v.isBlank()) return def;
        return Boolean.parseBoolean(v.trim());
    }
}
