package com.cp.ecoflux.util;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Lightweight tick-level profiler for debugging CPU bottlenecks.
 * Writes CSV to {@code logs/ecoflux-ticks.csv}.
 *
 * <p>Usage: call {@code start(label)} / {@code end(label)} around code blocks.
 * At the end of each tick, call {@code flushTick(gameTime)} to write a row.
 *
 * <p>For ultra-high-frequency spans (millions per second), use
 * {@link #start(String, int)} with a sample rate to record only every Nth call.
 */
public final class TickProfiler {
    public static final TickProfiler INSTANCE = new TickProfiler();
    private static final Path CSV_PATH = Paths.get("logs", "ecoflux-ticks.csv");

    private final ConcurrentLinkedQueue<Span> spans = new ConcurrentLinkedQueue<>();
    private final ConcurrentHashMap<String, AtomicLong> sampleCounters = new ConcurrentHashMap<>();
    private volatile boolean enabled;

    private TickProfiler() {
    }

    public void enable() {
        enabled = true;
        try {
            Files.deleteIfExists(CSV_PATH);
            writeHeader();
        } catch (IOException ignored) {
        }
    }

    public void disable() {
        enabled = false;
        sampleCounters.clear();
    }

    public boolean isEnabled() {
        return enabled;
    }

    private void writeHeader() throws IOException {
        ensureDir();
        try (BufferedWriter w = Files.newBufferedWriter(CSV_PATH, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            w.write("gameTime,label,durationNanos,extra");
            w.newLine();
        }
    }

    /** Record every call. Use for low-frequency spans. */
    public Span start(String label) {
        if (!enabled) return Span.NOOP;
        return new Span(label, System.nanoTime(), 0);
    }

    /**
     * Record only every {@code sampleRate}-th call.
     * Sample rate 1 = record every call (same as {@link #start(String)}).
     * Sample rate 1000 = record ~1/1000 calls. Use for million-call hot paths.
     */
    public Span start(String label, int sampleRate) {
        if (!enabled) return Span.NOOP;
        AtomicLong counter = sampleCounters.computeIfAbsent(label, k -> new AtomicLong());
        long n = counter.incrementAndGet();
        if (n % sampleRate != 0) {
            return new Span(label, 0, 1); // NOOP, but not Span.NOOP so we can track skip count
        }
        return new Span(label, System.nanoTime(), 0);
    }

    public void end(Span span) {
        if (span == Span.NOOP || !enabled) return;
        if (span.sampleRate > 0) {
            // Sampled span that was skipped: NOOP
            return;
        }
        span.durationNanos = System.nanoTime() - span.startNanos;
        spans.add(span);
    }

    public void record(String label, long durationNanos, String extra) {
        if (!enabled) return;
        spans.add(new Span(label, 0, durationNanos, extra));
    }

    public void flushTick(long gameTime) {
        if (!enabled || spans.isEmpty()) return;
        List<Span> batch = new ArrayList<>();
        Span s;
        while ((s = spans.poll()) != null) {
            batch.add(s);
        }
        if (batch.isEmpty()) return;

        try {
            ensureDir();
            try (BufferedWriter w = Files.newBufferedWriter(CSV_PATH, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                for (Span span : batch) {
                    w.write(String.format("%d,%s,%d,%s%n",
                            gameTime,
                            span.label.replace(',', ';'),
                            span.durationNanos,
                            span.extra != null ? span.extra.replace(',', ';') : ""));
                }
            }
        } catch (IOException ignored) {
        }
    }

    public long sampledCount(String label) {
        AtomicLong c = sampleCounters.get(label);
        return c != null ? c.get() : 0;
    }

    public void reportSampleRates() {
        if (sampleCounters.isEmpty()) return;
        System.out.println("[TickProfiler] sampled counters:");
        sampleCounters.forEach((label, counter) -> {
            System.out.printf("  %s: %d total calls (only ~1/Nth recorded)%n", label, counter.get());
        });
    }

    private static void ensureDir() throws IOException {
        Files.createDirectories(CSV_PATH.getParent());
    }

    public static final class Span {
        static final Span NOOP = new Span("", 0, 0, null);
        final String label;
        final long startNanos;
        long durationNanos;
        final String extra;
        final int sampleRate; // 0=live span, >0=sampled-out (skip in end())

        Span(String label, long startNanos, int sampleRate) {
            this(label, startNanos, 0, null, sampleRate);
        }

        Span(String label, long startNanos, long durationNanos, String extra) {
            this(label, startNanos, durationNanos, extra, 0);
        }

        Span(String label, long startNanos, long durationNanos, String extra, int sampleRate) {
            this.label = label;
            this.startNanos = startNanos;
            this.durationNanos = durationNanos;
            this.extra = extra;
            this.sampleRate = sampleRate;
        }
    }
}
