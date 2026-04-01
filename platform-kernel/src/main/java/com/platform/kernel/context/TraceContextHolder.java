package com.platform.kernel.context;

/**
 * 链路上下文持有器，用于在当前线程保存和读取追踪标识。
 */

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
