package com.misaka10843.create_railway_announcer.audio;

import java.nio.file.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class AudioCacheFiles {
    private static final Object LOCK = new Object();
    private static long epoch;
    public static final AtomicInteger PENDING = new AtomicInteger();
    private final Path root;
    public record Stats(long files, long bytes, int pending) {}
    public record Cleared(long deleted, long failed) {}
    public AudioCacheFiles(Path root) { this.root = root.toAbsolutePath().normalize(); }
    public static long epoch() { synchronized (LOCK) { return epoch; } }
    private static boolean owned(Path file) {
        String name = file.getFileName().toString();
        return name.matches("[a-f0-9]{64}\\.ogg") || name.startsWith("tts-") && (name.endsWith(".audio") || name.endsWith(".ogg"));
    }
    public Stats stats() {
        long count = 0, bytes = 0;
        synchronized (LOCK) {
            if (!Files.isDirectory(root)) return new Stats(0, 0, PENDING.get());
            try (var entries = Files.newDirectoryStream(root)) {
                for (Path file : entries) {
                    if (!owned(file) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) continue;
                    if (!file.getFileName().toString().startsWith("tts-")) count++;
                    bytes += Files.size(file);
                }
            } catch (java.io.IOException error) { throw new java.io.UncheckedIOException(error); }
        }
        return new Stats(count, bytes, PENDING.get());
    }
    public Cleared clear() {
        long deleted = 0, failed = 0;
        synchronized (LOCK) {
            epoch++;
            OggDurationReader.clearCache();
            if (!Files.isDirectory(root)) return new Cleared(0, 0);
            try (var entries = Files.newDirectoryStream(root)) {
                for (Path file : entries) {
                    if (!owned(file) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) continue;
                    try { if (Files.deleteIfExists(file)) deleted++; } catch (java.io.IOException error) { failed++; }
                }
            } catch (java.io.IOException error) { throw new java.io.UncheckedIOException(error); }
        }
        return new Cleared(deleted, failed);
    }
    public static boolean publish(long generation, Path encoded, Path output) throws java.io.IOException {
        synchronized (LOCK) {
            if (generation != epoch) return false;
            try { Files.move(encoded, output, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException error) { Files.move(encoded, output, StandardCopyOption.REPLACE_EXISTING); }
            return true;
        }
    }
}
