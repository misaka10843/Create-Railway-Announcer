package com.misaka10843.createrailwayannouncer.sequence;

import com.google.gson.JsonObject;
import com.misaka10843.createrailwayannouncer.audio.AudioCache;
import com.misaka10843.createrailwayannouncer.audio.AudioFragmentKey;
import com.misaka10843.createrailwayannouncer.audio.AudioFragmentType;
import com.misaka10843.createrailwayannouncer.config.ClientConfig;
import com.misaka10843.createrailwayannouncer.pack.PhraseEntry;
import com.misaka10843.createrailwayannouncer.pack.VoicePackManager;
import com.misaka10843.createrailwayannouncer.tts.TtsBackend;
import com.misaka10843.createrailwayannouncer.audio.AudioFragmentResult;

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
}