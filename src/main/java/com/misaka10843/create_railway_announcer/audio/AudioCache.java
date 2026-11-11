package com.misaka10843.create_railway_announcer.audio;

import com.misaka10843.create_railway_announcer.CreateRailwayAnnouncer;
import com.misaka10843.create_railway_announcer.config.ClientConfig;
import com.misaka10843.create_railway_announcer.tts.HttpTtsProvider;
import com.misaka10843.create_railway_announcer.tts.TtsProvider;
import com.misaka10843.create_railway_announcer.tts.TtsRequest;
import net.neoforged.fml.loading.FMLPaths;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.*;

public final class AudioCache {
    private static final ThreadPoolExecutor CONVERSION = new ThreadPoolExecutor(2, 2, 30, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(64), task -> {
                Thread thread = new Thread(task, "Railway Signage Audio Conversion");
                thread.setDaemon(true);
                return thread;
            });
    static { CONVERSION.allowCoreThreadTimeOut(true); }
    private final TtsProvider provider;
    private final AudioEncoder encoder;
    private final ConcurrentMap<Path, CompletableFuture<AudioFragmentResult>> pending = new ConcurrentHashMap<>();

    public AudioCache(TtsProvider provider) { this(provider, new LazyAudioEncoder()); }
    public AudioCache(TtsProvider provider, AudioEncoder encoder) {
        this.provider = provider;
        this.encoder = encoder;
    }

    public CompletableFuture<AudioFragmentResult> resolveOrGenerate(AudioFragmentKey key) {
        TtsProvider selected = provider.snapshot();
        if (!key.pitch().isBlank() && selected instanceof HttpTtsProvider http) selected = http.withPitch(key.pitch());
        Path output = resolvePath(key, selected);
        if (ClientConfig.CACHE_ENABLED.get() && Files.isRegularFile(output)) {
            return CompletableFuture.completedFuture(AudioFragmentResult.success(output));
        }
        if (!selected.isAvailable()) return CompletableFuture.completedFuture(AudioFragmentResult.failure("TTS disabled"));
        synchronized (pending) {
            var existing = pending.get(output);
            if (existing != null) return existing;
            var future = generate(key, output, selected);
            AudioCacheFiles.PENDING.incrementAndGet();
            pending.put(output, future);
            future.whenComplete((result, error) -> { AudioCacheFiles.PENDING.decrementAndGet(); synchronized (pending) { pending.remove(output, future); } });
            return future;
        }
    }

    private CompletableFuture<AudioFragmentResult> generate(AudioFragmentKey key, Path output, TtsProvider selected) {
        try {
            long generation = AudioCacheFiles.epoch();
            Files.createDirectories(output.getParent());
            Path input = Files.createTempFile(output.getParent(), "tts-", ".audio");
            Path encoded = Files.createTempFile(output.getParent(), "tts-", ".ogg");
            var request = new TtsRequest(key.backend(), key.language(), key.voiceContains(), key.rate(), key.volume(), key.input(), key.ssml(), input);
            return selected.synthesize(request).thenApplyAsync(result -> {
                if (generation != AudioCacheFiles.epoch()) return AudioFragmentResult.failure("Audio cache cleared while generating");
                if (!result.ok()) return AudioFragmentResult.failure(result.error());
                var converted = encoder.encodeToOgg(input, encoded);
                if (!converted.ok()) return AudioFragmentResult.failure(converted.error());
                try {
                    if (!AudioCacheFiles.publish(generation, encoded, output)) return AudioFragmentResult.failure("Audio cache cleared while generating");
                    return AudioFragmentResult.success(output);
                } catch (Exception error) { return AudioFragmentResult.failure("Cannot store TTS audio"); }
            }, CONVERSION).exceptionally(error -> AudioFragmentResult.failure("TTS generation failed"))
                    .whenComplete((result, error) -> {
                        try { Files.deleteIfExists(input); Files.deleteIfExists(encoded); }
                        catch (Exception ignored) {}
                    });
        } catch (Exception error) {
            return CompletableFuture.completedFuture(AudioFragmentResult.failure("Cannot create TTS cache"));
        }
    }

    public Path resolvePath(AudioFragmentKey key) {
        TtsProvider selected = provider.snapshot();
        if (!key.pitch().isBlank() && selected instanceof HttpTtsProvider http) selected = http.withPitch(key.pitch());
        return resolvePath(key, selected);
    }

    private Path resolvePath(AudioFragmentKey key, TtsProvider selected) {
        try {
            String identity = selected instanceof HttpTtsProvider http ? http.cacheIdentity() : selected.id();
            byte[] digest = MessageDigest.getInstance("SHA-256").digest((identity + "\n" + key).getBytes(StandardCharsets.UTF_8));
            return root().resolve(HexFormat.of().formatHex(digest) + ".ogg");
        } catch (Exception error) { throw new IllegalStateException(error); }
    }

    public static Path cacheRoot() { return FMLPaths.CONFIGDIR.get().resolve(CreateRailwayAnnouncer.MODID).resolve("announcer/cache"); }
    public static AudioCacheFiles cacheFiles() { return new AudioCacheFiles(cacheRoot()); }
    public Path root() { return cacheRoot(); }
    public AudioCacheFiles files() { return cacheFiles(); }

    private static final class LazyAudioEncoder implements AudioEncoder {
        private AudioEncoder delegate;
        private String failure;
        @Override public String id() { return "lazy_ogg_vorbis"; }
        @Override public synchronized AudioEncodeResult encodeToOgg(Path input, Path output) {
            if (failure != null) return AudioEncodeResult.failure(failure);
            try {
                if (delegate == null) delegate = new JaveOggEncoder();
                return delegate.encodeToOgg(input, output);
            } catch (LinkageError | RuntimeException error) {
                failure = "Audio encoder unavailable: " + error.getClass().getSimpleName();
                CreateRailwayAnnouncer.LOGGER.error("Cannot initialize announcement audio encoder", error);
                return AudioEncodeResult.failure(failure);
            }
        }
    }
}
