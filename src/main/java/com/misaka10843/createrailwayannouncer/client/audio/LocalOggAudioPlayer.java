package com.misaka10843.createrailwayannouncer.client.audio;

import com.misaka10843.createrailwayannouncer.CreateRailwayAnnouncer;
import net.minecraft.client.Minecraft;
import org.lwjgl.BufferUtils;
import org.lwjgl.openal.AL10;
import org.lwjgl.stb.STBVorbis;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.libc.LibCStdlib;

import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class LocalOggAudioPlayer {
    private final ScheduledExecutorService pollExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "Create Railway Announcer Local OGG Poller");
        thread.setDaemon(true);
        return thread;
    });

    private final Set<ActiveSound> activeSounds = ConcurrentHashMap.newKeySet();
    private static final int AL_SEC_OFFSET = 0x1024;

    private static void freeStbPcm(ShortBuffer pcm) {
        if (pcm == null) {
            return;
        }

        try {
            LibCStdlib.nfree(MemoryUtil.memAddress(pcm));
        } catch (Throwable t) {
            CreateRailwayAnnouncer.LOGGER.warn("Failed to free STB decoded PCM buffer safely", t);
        }
    }

    private static void safeDeleteSource(int source) {
        try {
            AL10.alSourceStop(source);
            AL10.alDeleteSources(source);
        } catch (Throwable ignored) {
        }
    }

    private static void safeDeleteBuffer(int buffer) {
        try {
            AL10.alDeleteBuffers(buffer);
        } catch (Throwable ignored) {
        }
    }

    private static void checkAlError(String stage) {
        int error = AL10.alGetError();
        if (error != AL10.AL_NO_ERROR) {
            throw new IllegalStateException(stage + " OpenAL error: " + error);
        }
    }

    private static float clampGain(float gain) {
        return Math.max(0.0F, Math.min(1.0F, gain));
    }

    public CompletableFuture<Void> play(Path oggPath) {
        return play(oggPath, 1.0F, 150);
    }

    public CompletableFuture<Void> play(Path oggPath, float targetGain, int fadeInMs) {
        return play(oggPath, targetGain, fadeInMs, 0L);
    }

    public CompletableFuture<Void> play(
            Path oggPath,
            float targetGain,
            int fadeInMs,
            long startOffsetMs
    ) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        if (oggPath == null) {
            future.completeExceptionally(new IllegalArgumentException("oggPath is null"));
            return future;
        }

        if (!Files.isRegularFile(oggPath)) {
            future.completeExceptionally(new IllegalArgumentException("OGG file does not exist: " + oggPath));
            return future;
        }

        String fileName = oggPath.getFileName().toString().toLowerCase(Locale.ROOT);
        if (!fileName.endsWith(".ogg")) {
            future.completeExceptionally(new IllegalArgumentException("Only OGG files are supported: " + oggPath));
            return future;
        }

        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> startOnRenderThread(
                minecraft,
                oggPath,
                targetGain,
                fadeInMs,
                Math.max(0L, startOffsetMs),
                future
        ));

        return future;
    }

    public void stopAll() {
        fadeOutAndStopAll(0);
    }

    public void fadeOutAndStopAll(int fadeOutMs) {
        Minecraft minecraft = Minecraft.getInstance();

        minecraft.execute(() -> {
            if (activeSounds.isEmpty()) {
                return;
            }

            for (ActiveSound sound : Set.copyOf(activeSounds)) {
                if (!sound.beginClosing()) {
                    continue;
                }

                if (fadeOutMs <= 0) {
                    cleanup(sound, true);
                    continue;
                }

                try {
                    float currentGain = AL10.alGetSourcef(sound.source(), AL10.AL_GAIN);
                    int error = AL10.alGetError();
                    CreateRailwayAnnouncer.LOGGER.info(
                            "Fading out local OGG source {}, currentGain={}, fadeOutMs={}",
                            sound.source(),
                            currentGain,
                            fadeOutMs
                    );
                    if (error != AL10.AL_NO_ERROR) {
                        cleanup(sound, true);
                        continue;
                    }

                    fadeGain(minecraft, sound, currentGain, 0.0F, fadeOutMs);

                    pollExecutor.schedule(() -> minecraft.execute(() -> {
                        cleanup(sound, true);
                    }), fadeOutMs + 50L, TimeUnit.MILLISECONDS);
                } catch (Throwable t) {
                    cleanup(sound, true);
                }
            }
        });
    }

    private void startOnRenderThread(
            Minecraft minecraft,
            Path oggPath,
            float targetGain,
            int fadeInMs,
            long startOffsetMs,
            CompletableFuture<Void> future
    ) {
        int buffer = 0;
        int source = 0;
        ShortBuffer pcm = null;

        try {
            IntBuffer channelsBuffer = BufferUtils.createIntBuffer(1);
            IntBuffer sampleRateBuffer = BufferUtils.createIntBuffer(1);

            pcm = STBVorbis.stb_vorbis_decode_filename(
                    oggPath.toAbsolutePath().toString(),
                    channelsBuffer,
                    sampleRateBuffer
            );

            if (pcm == null) {
                throw new IllegalStateException("Failed to decode OGG: " + oggPath);
            }

            int channels = channelsBuffer.get(0);
            int sampleRate = sampleRateBuffer.get(0);

            int format = switch (channels) {
                case 1 -> AL10.AL_FORMAT_MONO16;
                case 2 -> AL10.AL_FORMAT_STEREO16;
                default -> throw new IllegalStateException("Unsupported OGG channel count: " + channels);
            };

            buffer = AL10.alGenBuffers();
            checkAlError("alGenBuffers");

            AL10.alBufferData(buffer, format, pcm, sampleRate);
            checkAlError("alBufferData");

            source = AL10.alGenSources();
            checkAlError("alGenSources");

            AL10.alSourcei(source, AL10.AL_SOURCE_RELATIVE, AL10.AL_TRUE);
            AL10.alSource3f(source, AL10.AL_POSITION, 0.0F, 0.0F, 0.0F);
            AL10.alSource3f(source, AL10.AL_VELOCITY, 0.0F, 0.0F, 0.0F);

            AL10.alSourcef(source, AL10.AL_ROLLOFF_FACTOR, 0.0F);
            AL10.alSourcef(source, AL10.AL_REFERENCE_DISTANCE, 1.0F);
            AL10.alSourcef(source, AL10.AL_MAX_DISTANCE, 100000.0F);

            AL10.alSourcei(source, AL10.AL_BUFFER, buffer);

            if (startOffsetMs > 0L) {
                float offsetSeconds = startOffsetMs / 1000.0F;
                AL10.alSourcef(source, AL_SEC_OFFSET, offsetSeconds);

                int offsetError = AL10.alGetError();
                if (offsetError != AL10.AL_NO_ERROR) {
                    CreateRailwayAnnouncer.LOGGER.warn(
                            "Failed to set OGG playback offset: file={}, offsetMs={}, alError={}",
                            oggPath,
                            startOffsetMs,
                            offsetError
                    );
                }
            }

            float clampedTargetGain = clampGain(targetGain);
            float initialGain = fadeInMs > 0 ? 0.0F : clampedTargetGain;
            AL10.alSourcef(source, AL10.AL_GAIN, initialGain);

            AL10.alSourcePlay(source);
            checkAlError("alSourcePlay");

            ActiveSound activeSound = new ActiveSound(source, buffer, future);
            activeSounds.add(activeSound);

            if (fadeInMs > 0) {
                fadeGain(minecraft, activeSound, 0.0F, clampedTargetGain, fadeInMs);
            }

            CreateRailwayAnnouncer.LOGGER.info(
                    "Started local OGG playback: {}, channels={}, sampleRate={}, samples={}, targetGain={}, fadeInMs={}, startOffsetMs={}",
                    oggPath,
                    channels,
                    sampleRate,
                    pcm.remaining(),
                    clampedTargetGain,
                    fadeInMs,
                    startOffsetMs
            );

            schedulePoll(minecraft, activeSound);
        } catch (Throwable t) {
            CreateRailwayAnnouncer.LOGGER.error("Failed to start local OGG playback: {}", oggPath, t);

            if (source != 0) {
                safeDeleteSource(source);
            }
            if (buffer != 0) {
                safeDeleteBuffer(buffer);
            }

            future.completeExceptionally(t);
        } finally {
            freeStbPcm(pcm);
        }
    }

    private void schedulePoll(Minecraft minecraft, ActiveSound sound) {
        pollExecutor.schedule(() -> minecraft.execute(() -> {
            if (sound.future().isDone()) {
                cleanup(sound, false);
                return;
            }

            if (!activeSounds.contains(sound)) {
                cleanup(sound, true);
                return;
            }

            try {
                int state = AL10.alGetSourcei(sound.source(), AL10.AL_SOURCE_STATE);
                int error = AL10.alGetError();

                if (error != AL10.AL_NO_ERROR) {
                    if (error == AL10.AL_INVALID_NAME || sound.isClosing()) {
                        cleanup(sound, true);
                        return;
                    }

                    cleanup(sound, false);
                    sound.future().completeExceptionally(new IllegalStateException("OpenAL error while polling source: " + error));
                    return;
                }

                if (state == AL10.AL_PLAYING) {
                    schedulePoll(minecraft, sound);
                    return;
                }

                cleanup(sound, true);
            } catch (Throwable t) {
                if (sound.isClosing() || !activeSounds.contains(sound)) {
                    cleanup(sound, true);
                    return;
                }

                cleanup(sound, false);
                sound.future().completeExceptionally(t);
            }
        }), 50L, TimeUnit.MILLISECONDS);
    }

    private void fadeGain(Minecraft minecraft, ActiveSound sound, float from, float to, int durationMs) {
        int steps = Math.max(1, durationMs / 25);

        for (int i = 1; i <= steps; i++) {
            int step = i;
            long delayMs = (long) i * durationMs / steps;

            pollExecutor.schedule(() -> minecraft.execute(() -> {
                try {
                    if (sound.future().isDone() || !activeSounds.contains(sound)) {
                        return;
                    }

                    int state = AL10.alGetSourcei(sound.source(), AL10.AL_SOURCE_STATE);
                    int error = AL10.alGetError();

                    if (error != AL10.AL_NO_ERROR || state == AL10.AL_STOPPED || state == 0) {
                        return;
                    }

                    float t = step / (float) steps;
                    float gain = from + (to - from) * t;
                    AL10.alSourcef(sound.source(), AL10.AL_GAIN, clampGain(gain));
                } catch (Throwable ignored) {
                }
            }), delayMs, TimeUnit.MILLISECONDS);
        }
    }

    private void cleanup(ActiveSound sound, boolean completeFuture) {
        if (!sound.cleaned.compareAndSet(false, true)) {
            return;
        }

        activeSounds.remove(sound);
        safeDeleteSource(sound.source());
        safeDeleteBuffer(sound.buffer());

        if (completeFuture && !sound.future().isDone()) {
            sound.future().complete(null);
        }
    }

    private static final class ActiveSound {
        private final int source;
        private final int buffer;
        private final CompletableFuture<Void> future;
        private final AtomicBoolean closing = new AtomicBoolean(false);
        private final AtomicBoolean cleaned = new AtomicBoolean(false);

        private ActiveSound(int source, int buffer, CompletableFuture<Void> future) {
            this.source = source;
            this.buffer = buffer;
            this.future = future;
        }

        public int source() {
            return source;
        }

        public int buffer() {
            return buffer;
        }

        public CompletableFuture<Void> future() {
            return future;
        }

        public boolean beginClosing() {
            return closing.compareAndSet(false, true);
        }

        public boolean isClosing() {
            return closing.get();
        }
    }
}