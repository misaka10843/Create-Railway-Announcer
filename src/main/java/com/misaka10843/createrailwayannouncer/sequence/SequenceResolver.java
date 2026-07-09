package com.misaka10843.createrailwayannouncer.sequence;

import com.google.gson.JsonObject;
import com.misaka10843.createrailwayannouncer.audio.AudioCache;
import com.misaka10843.createrailwayannouncer.audio.AudioFragmentKey;
import com.misaka10843.createrailwayannouncer.audio.AudioFragmentResult;
import com.misaka10843.createrailwayannouncer.audio.AudioFragmentType;
import com.misaka10843.createrailwayannouncer.config.ClientConfig;
import com.misaka10843.createrailwayannouncer.pack.PhraseEntry;
import com.misaka10843.createrailwayannouncer.pack.VoicePackManager;
import com.misaka10843.createrailwayannouncer.tts.TtsBackend;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public final class SequenceResolver {
    private final AudioCache audioCache;

    public SequenceResolver(AudioCache audioCache) {
        this.audioCache = audioCache;
    }

    public CompletableFuture<List<AudioFragmentResult>> resolveAudioFragments(SequenceTemplate template) {
        List<CompletableFuture<AudioFragmentResult>> futures = new ArrayList<>();
        for (SequenceItem item : template.items()) {
            switch (item.type()) {
                case PHRASE -> resolvePhrase(item.raw(), futures);
                case STATION -> resolveStation(item.raw(), futures);
                case LINE -> resolveLine(item.raw(), futures);
                case PAUSE, SOUND, SUBTITLE, CONDITION, TRANSFER_LINES, DOOR_SIDE_PHRASE, TTS_RAW -> {
                }
            }
        }

        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                .thenApply(ignored -> futures.stream().map(CompletableFuture::join).toList());
    }

    public CompletableFuture<ResolvedSequence> resolveSequence(SequenceTemplate template) {
        List<CompletableFuture<List<ResolvedSequenceItem>>> futures = new ArrayList<>();

        for (SequenceItem item : template.items()) {
            futures.add(resolveItem(item));
        }

        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                .thenApply(ignored -> {
                    List<ResolvedSequenceItem> resolvedItems = new ArrayList<>();

                    for (CompletableFuture<List<ResolvedSequenceItem>> future : futures) {
                        resolvedItems.addAll(future.join());
                    }

                    return new ResolvedSequence(
                            template.id(),
                            template.event(),
                            template.channel(),
                            template.priority(),
                            resolvedItems
                    );
                });
    }

    private CompletableFuture<List<ResolvedSequenceItem>> resolveItem(SequenceItem item) {
        return switch (item.type()) {
            case PHRASE -> resolvePhraseItem(item.raw());
            case STATION -> resolveStationItem(item.raw());
            case LINE -> resolveLineItem(item.raw());
            case PAUSE -> CompletableFuture.completedFuture(resolvePauseItem(item.raw()));
            case SUBTITLE -> CompletableFuture.completedFuture(resolveSubtitleItem(item.raw()));
            case SOUND -> CompletableFuture.completedFuture(resolveSoundItem(item.raw()));
            case CONDITION, TRANSFER_LINES, DOOR_SIDE_PHRASE, TTS_RAW -> CompletableFuture.completedFuture(List.of());
        };
    }

    private CompletableFuture<List<ResolvedSequenceItem>> resolvePhraseItem(JsonObject raw) {
        String id = readString(raw, "id", "");
        if (id.isBlank()) {
            return CompletableFuture.completedFuture(List.of());
        }

        PhraseEntry entry = VoicePackManager.phrases().get(id).orElse(null);
        if (entry == null) {
            return CompletableFuture.completedFuture(List.of());
        }

        AudioFragmentKey key = createKey(
                AudioFragmentType.COMMON,
                entry.id(),
                "default",
                entry.text(),
                entry.ssml()
        );

        return audioCache.resolveOrGenerate(key).thenApply(result -> toAudioItem(result));
    }

    private CompletableFuture<List<ResolvedSequenceItem>> resolveStationItem(JsonObject raw) {
        String id = readString(raw, "id", "");
        String variant = readString(raw, "variant", "default");
        String text = readString(raw, "text", "");

        if (id.isBlank() || text.isBlank()) {
            return CompletableFuture.completedFuture(List.of());
        }

        AudioFragmentKey key = createKey(
                AudioFragmentType.STATION,
                id,
                variant,
                text,
                false
        );

        return audioCache.resolveOrGenerate(key).thenApply(result -> toAudioItem(result));
    }

    private CompletableFuture<List<ResolvedSequenceItem>> resolveLineItem(JsonObject raw) {
        String id = readString(raw, "id", "");
        String variant = readString(raw, "variant", "default");
        String text = readString(raw, "text", "");

        if (id.isBlank() || text.isBlank()) {
            return CompletableFuture.completedFuture(List.of());
        }

        AudioFragmentKey key = createKey(
                AudioFragmentType.LINE,
                id,
                variant,
                text,
                false
        );

        return audioCache.resolveOrGenerate(key).thenApply(result -> toAudioItem(result));
    }

    private List<ResolvedSequenceItem> resolvePauseItem(JsonObject raw) {
        int durationMs = readInt(raw, "duration_ms", 0);
        return List.of(ResolvedSequenceItem.pause(durationMs));
    }

    private List<ResolvedSequenceItem> resolveSubtitleItem(JsonObject raw) {
        String text = readString(raw, "text", "");
        if (text.isBlank()) {
            return List.of();
        }
        return List.of(ResolvedSequenceItem.subtitle(text));
    }

    private List<ResolvedSequenceItem> resolveSoundItem(JsonObject raw) {
        String file = readString(raw, "file", "");
        if (file.isBlank()) {
            return List.of();
        }

        Path soundPath = VoicePackManager.activePack()
                .map(pack -> pack.root().resolve("sounds").resolve(file))
                .orElse(null);

        if (soundPath == null || !Files.isRegularFile(soundPath)) {
            return List.of();
        }

        String name = soundPath.getFileName().toString().toLowerCase(Locale.ROOT);
        if (!name.endsWith(".ogg")) {
            return List.of();
        }

        return List.of(ResolvedSequenceItem.sound(soundPath));
    }

    private List<ResolvedSequenceItem> toAudioItem(AudioFragmentResult result) {
        if (!result.ok()) {
            return List.of();
        }

        if (result.audioPath() == null) {
            return List.of();
        }

        String name = result.audioPath().getFileName().toString().toLowerCase(Locale.ROOT);
        if (!name.endsWith(".ogg")) {
            return List.of();
        }

        return List.of(ResolvedSequenceItem.audio(result.audioPath()));
    }

    private void resolvePhrase(JsonObject raw, List<CompletableFuture<AudioFragmentResult>> futures) {
        String id = readString(raw, "id", "");
        if (id.isBlank()) {
            return;
        }

        PhraseEntry entry = VoicePackManager.phrases().get(id).orElse(null);
        if (entry == null) {
            return;
        }

        AudioFragmentKey key = createKey(
                AudioFragmentType.COMMON,
                entry.id(),
                "default",
                entry.text(),
                entry.ssml()
        );

        futures.add(audioCache.resolveOrGenerate(key));
    }

    private void resolveStation(JsonObject raw, List<CompletableFuture<AudioFragmentResult>> futures) {
        String id = readString(raw, "id", "");
        String variant = readString(raw, "variant", "default");
        String text = readString(raw, "text", "");

        if (id.isBlank() || text.isBlank()) {
            return;
        }

        AudioFragmentKey key = createKey(
                AudioFragmentType.STATION,
                id,
                variant,
                text,
                false
        );

        futures.add(audioCache.resolveOrGenerate(key));
    }

    private void resolveLine(JsonObject raw, List<CompletableFuture<AudioFragmentResult>> futures) {
        String id = readString(raw, "id", "");
        String variant = readString(raw, "variant", "default");
        String text = readString(raw, "text", "");

        if (id.isBlank() || text.isBlank()) {
            return;
        }

        AudioFragmentKey key = createKey(
                AudioFragmentType.LINE,
                id,
                variant,
                text,
                false
        );

        futures.add(audioCache.resolveOrGenerate(key));
    }

    private AudioFragmentKey createKey(
            AudioFragmentType type,
            String id,
            String variant,
            String input,
            boolean ssml
    ) {
        return new AudioFragmentKey(
                type,
                id,
                variant,
                readBackend(),
                ClientConfig.TTS_LANGUAGE.get(),
                ClientConfig.TTS_PREFERRED_VOICE_CONTAINS.get(),
                ClientConfig.TTS_RATE.get(),
                ClientConfig.TTS_VOLUME.get(),
                input,
                ssml
        );
    }

    private static TtsBackend readBackend() {
        String value = ClientConfig.TTS_BACKEND.get().trim().toUpperCase(Locale.ROOT);
        try {
            return TtsBackend.valueOf(value);
        } catch (IllegalArgumentException e) {
            return TtsBackend.AUTO;
        }
    }

    private static String readString(JsonObject object, String key, String fallback) {
        if (!object.has(key) || object.get(key).isJsonNull()) {
            return fallback;
        }
        return object.get(key).getAsString();
    }

    private static int readInt(JsonObject object, String key, int fallback) {
        if (!object.has(key) || object.get(key).isJsonNull()) {
            return fallback;
        }
        return object.get(key).getAsInt();
    }
}