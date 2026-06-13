package com.s.ecoflux.util;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Lightweight tick-level profiler for debugging CPU bottlenecks.
 * Writes CSV to {@code logs/ecoflux-ticks.csv}.
 *
 * <p>Usage: call {@code start(label)} / {@code end(label)} around code blocks.
 * At the end of each tick, call {@code flushTick(gameTime)} to write a row.
 */
public final class TickProfiler {
    public static final TickProfiler INSTANCE = new TickProfiler();
    private static final Path CSV_PATH = Paths.get("logs", "ecoflux-ticks.csv");

    private final ConcurrentLinkedQueue<Span> spans = new ConcurrentLinkedQueue<>();
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

    public Span start(String label) {
        if (!enabled) return Span.NOOP;
        return new Span(label, System.nanoTime());
    }

    public void end(Span span) {
        if (span == Span.NOOP || !enabled) return;
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

    private static void ensureDir() throws IOException {
        Files.createDirectories(CSV_PATH.getParent());
    }

    public static final class Span {
        static final Span NOOP = new Span("", 0, 0, null);
        final String label;
        final long startNanos;
        long durationNanos;
        final String extra;

        Span(String label, long startNanos) {
            this(label, startNanos, 0, null);
        }

        Span(String label, long startNanos, long durationNanos, String extra) {
            this.label = label;
            this.startNanos = startNanos;
            this.durationNanos = durationNanos;
            this.extra = extra;
        }
    }
}
