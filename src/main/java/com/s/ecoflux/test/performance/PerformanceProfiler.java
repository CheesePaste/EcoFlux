package com.s.ecoflux.test.performance;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lightweight stack-based performance profiler for identifying bottlenecks.
 *
 * <p>Designed to work with Mixin injection — each target method gets a
 * {@link #push} at HEAD and {@link #pop} at RETURN via Mixin {@code @Inject}.
 * The ThreadLocal stack handles nested spans automatically. When disabled,
 * push/pop are near-zero overhead (a single volatile read + branch).
 *
 * <p>Statistics aggregate per span name: count, total, min, max, average.
 */
public final class PerformanceProfiler {
    public static final PerformanceProfiler INSTANCE = new PerformanceProfiler();

    private static final int RING_SIZE = 64;

    private volatile boolean enabled;
    private final Map<String, SpanStats> stats = new ConcurrentHashMap<>();
    private final ThreadLocal<Deque<Frame>> stack = ThreadLocal.withInitial(ArrayDeque::new);

    private record Frame(String name, long startNs) {}

    private PerformanceProfiler() {
    }

    public void enable() {
        enabled = true;
    }

    public void disable() {
        enabled = false;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void reset() {
        stats.clear();
    }

    /** Push a named span onto the thread-local stack. No-op when disabled. */
    public void push(String name) {
        if (!enabled) return;
        stack.get().push(new Frame(name, System.nanoTime()));
    }

    /** Pop the most recent span and record its elapsed time. No-op when disabled. */
    public void pop() {
        if (!enabled) return;
        Frame frame = stack.get().pollFirst();
        if (frame == null) return;
        long elapsed = System.nanoTime() - frame.startNs;
        stats.computeIfAbsent(frame.name, SpanStats::new).record(elapsed);
    }

    public String report(int topN) {
        if (stats.isEmpty()) {
            return "性能追踪数据为空。使用 /ecoflux profile on 启用追踪。";
        }

        List<Map.Entry<String, SpanStats>> sorted = new ArrayList<>(stats.entrySet());
        sorted.sort(Comparator.<Map.Entry<String, SpanStats>>comparingLong(e -> e.getValue().totalNs).reversed());

        int limit = Math.min(topN, sorted.size());
        StringBuilder sb = new StringBuilder();
        sb.append("=== Ecoflux 性能报告 (top ").append(limit).append(" by total time) ===\n");
        sb.append(String.format("%-30s %8s %10s %10s %10s %10s\n",
                "span", "count", "total(ms)", "avg(us)", "min(us)", "max(us)"));

        for (int i = 0; i < limit; i++) {
            Map.Entry<String, SpanStats> entry = sorted.get(i);
            SpanStats s = entry.getValue();
            sb.append(String.format("%-30s %8d %10.2f %10.1f %10.1f %10.1f\n",
                    entry.getKey(),
                    s.count,
                    s.totalNs / 1_000_000.0,
                    s.avgNs() / 1000.0,
                    s.minNs / 1000.0,
                    s.maxNs / 1000.0));
        }

        long totalAll = stats.values().stream().mapToLong(s -> s.totalNs).sum();
        sb.append(String.format("--- 所有 %d 个 span 总耗时: %.2f ms ---", stats.size(), totalAll / 1_000_000.0));
        return sb.toString();
    }

    public String status() {
        if (!enabled) {
            return "性能追踪: 已禁用。使用 /ecoflux profile on 启用。";
        }
        return "性能追踪: 已启用。追踪 " + stats.size() + " 个 span。使用 /ecoflux profile report 查看。";
    }

    private static final class SpanStats {
        long count;
        long totalNs;
        long minNs = Long.MAX_VALUE;
        long maxNs;
        final long[] ring = new long[RING_SIZE];
        int ringIdx;

        SpanStats(String name) {
        }

        synchronized void record(long ns) {
            count++;
            totalNs += ns;
            if (ns < minNs) minNs = ns;
            if (ns > maxNs) maxNs = ns;
            ring[ringIdx % RING_SIZE] = ns;
            ringIdx++;
        }

        double avgNs() {
            long c = count;
            return c > 0 ? (double) totalNs / c : 0;
        }
    }
}
