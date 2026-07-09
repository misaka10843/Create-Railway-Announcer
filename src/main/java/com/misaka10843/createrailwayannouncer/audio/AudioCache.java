package com.misaka10843.createrailwayannouncer.audio;

import com.misaka10843.createrailwayannouncer.CreateRailwayAnnouncer;
import com.misaka10843.createrailwayannouncer.tts.TtsRequest;
import com.misaka10843.createrailwayannouncer.tts.WindowsBridgeTtsProvider;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

public final class AudioCache {
    private final WindowsBridgeTtsProvider provider;
    private AudioEncoder encoder;

    public AudioCache(WindowsBridgeTtsProvider provider) {
        this(provider, null);
    }

    public AudioCache(WindowsBridgeTtsProvider provider, AudioEncoder encoder) {
        this.provider = provider;
        this.encoder = encoder;
    }

    private static AudioEncoder createDefaultEncoder() {
        try {
            return new JaveOggEncoder();
        } catch (Throwable t) {
            CreateRailwayAnnouncer.LOGGER.error("Failed to initialize JAVE OGG encoder", t);

            return new AudioEncoder() {
                @Override
                public String id() {
                    return "unavailable_ogg_encoder";
                }

                @Override
                public AudioEncodeResult encodeWavToOgg(Path wavInput, Path oggOutput) {
                    return AudioEncodeResult.failure(
                            "JAVE OGG encoder is not available: "
                                    + t.getClass().getSimpleName()
                                    + ": "
                                    + t.getMessage()
                    );
                }
            };
        }
    }

    private static void cleanupTemp(Path tempWav) {
        try {
            Files.deleteIfExists(tempWav);
        } catch (IOException e) {
            CreateRailwayAnnouncer.LOGGER.debug("Failed to delete temp wav {}", tempWav, e);
        }
    }

    private static void cleanupBrokenOutput(Path output) {
        try {
            Files.deleteIfExists(output);
        } catch (IOException e) {
            CreateRailwayAnnouncer.LOGGER.debug("Failed to delete broken ogg {}", output, e);
        }
    }

    private AudioEncoder encoder() {
        if (encoder == null) {
            encoder = createDefaultEncoder();
        }
        return encoder;
    }

    public CompletableFuture<AudioFragmentResult> resolveOrGenerate(AudioFragmentKey key) {
        Path oggOutput = resolvePath(key);

        if (Files.isRegularFile(oggOutput)) {
            CreateRailwayAnnouncer.LOGGER.info("Audio cache hit: {}", oggOutput);
            return CompletableFuture.completedFuture(AudioFragmentResult.success(oggOutput));
        }

        CreateRailwayAnnouncer.LOGGER.info("Audio cache miss: {}", oggOutput);

        try {
            Files.createDirectories(oggOutput.getParent());
            Files.createDirectories(tempRoot());
        } catch (IOException e) {
            return CompletableFuture.completedFuture(AudioFragmentResult.failure("Failed to create cache directory: " + e.getMessage()));
        }

        Path tempWav = resolveTempWavPath(key);

        TtsRequest request = new TtsRequest(
                key.backend(),
                key.language(),
                key.voiceContains(),
                key.rate(),
                key.volume(),
                key.input(),
                key.ssml(),
                tempWav
        );

        return provider.synthesize(request).thenApply(ttsResult -> {
            try {
                if (!ttsResult.ok()) {
                    cleanupTemp(tempWav);
                    return AudioFragmentResult.failure(ttsResult.error());
                }

                if (!Files.isRegularFile(tempWav)) {
                    cleanupTemp(tempWav);
                    return AudioFragmentResult.failure("Bridge reported success, but temp wav was not found: " + tempWav);
                }

                AudioEncodeResult encodeResult = encoder().encodeWavToOgg(tempWav, oggOutput);
                cleanupTemp(tempWav);

                if (!encodeResult.ok()) {
                    cleanupBrokenOutput(oggOutput);
                    return AudioFragmentResult.failure("Failed to encode ogg: " + encodeResult.error());
                }

                if (!Files.isRegularFile(oggOutput)) {
                    return AudioFragmentResult.failure("Encoder reported success, but ogg was not found: " + oggOutput);
                }

                return AudioFragmentResult.success(oggOutput);
            } catch (Throwable t) {
                cleanupTemp(tempWav);
                cleanupBrokenOutput(oggOutput);
                CreateRailwayAnnouncer.LOGGER.error("Audio fragment generation failed", t);
                return AudioFragmentResult.failure(t.getClass().getSimpleName() + ": " + t.getMessage());
            }
        });
    }

    public Path resolvePath(AudioFragmentKey key) {
        return root()
                .resolve("voices")
                .resolve(key.backend().name().toLowerCase())
                .resolve(key.voiceDirectory())
                .resolve(key.language())
                .resolve(key.rateVolumeDirectory())
                .resolve(key.type().directoryName())
                .resolve(key.fileName());
    }

    public Path resolveTempWavPath(AudioFragmentKey key) {
        return tempRoot().resolve(key.tempWavFileName());
    }

    public Path root() {
        return FMLPaths.CONFIGDIR.get()
                .resolve(CreateRailwayAnnouncer.MODID)
                .resolve("cache");
    }

    public Path tempRoot() {
        return root().resolve("tmp");
    }
}