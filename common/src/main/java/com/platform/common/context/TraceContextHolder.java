package com.platform.common.context;

public final class TraceContextHolder {

    private static final ThreadLocal<String> HOLDER = new ThreadLocal<>();

    private TraceContextHolder() {
    }

    public static void set(String traceId) {
        HOLDER.set(traceId);
    }

    public static String get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
