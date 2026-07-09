package com.misaka10843.createrailwayannouncer.sequence;

import com.google.gson.JsonObject;
import com.misaka10843.createrailwayannouncer.audio.AudioCache;
import com.misaka10843.createrailwayannouncer.audio.AudioFragmentKey;
import com.misaka10843.createrailwayannouncer.audio.AudioFragmentResult;
import com.misaka10843.createrailwayannouncer.audio.AudioFragmentType;
import com.misaka10843.createrailwayannouncer.config.*;
import com.misaka10843.createrailwayannouncer.pack.PhraseEntry;
import com.misaka10843.createrailwayannouncer.pack.VoicePackManager;
import com.misaka10843.createrailwayannouncer.tts.TtsBackend;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class SequenceResolver {
    private final AudioCache audioCache;

    public SequenceResolver(AudioCache audioCache) {
        this.audioCache = audioCache;
    }

    private static String doorSidePhraseId(AnnouncementResolveContext context) {
        if (context == null || context.station() == null) {
            return "";
        }

        DoorSide doorSide = context.station().getDoorSide();

        return switch (doorSide) {
            case LEFT -> "common.door_left";
            case RIGHT -> "common.door_right";
            case BOTH -> "common.door_both";
            case NONE -> "";
        };
    }

    private static String replaceContextVariables(
            String text,
            AnnouncementResolveContext context
    ) {
        if (context == null || context.station() == null) {
            return text;
        }

        StationConfig station = context.station();
        String stationDisplay = localized(station.getDisplay(), station.getReading(), station.getCustomId());
        String stationReading = localized(station.getReading(), station.getDisplay(), station.getCustomId());

        return text
                .replace("{station}", stationDisplay)
                .replace("{station_reading}", stationReading)
                .replace("{platform}", station.getPlatform() == null ? "" : station.getPlatform());
    }

    private static String localized(
            Map<String, String> primary,
            Map<String, String> fallback,
            String fallbackText
    ) {
        String language = ClientConfig.TTS_LANGUAGE.get()
                .trim()
                .toLowerCase(Locale.ROOT)
                .replace('-', '_');

        String value = primary.get(language);
        if (value != null && !value.isBlank()) {
            return value;
        }

        value = primary.get("ja_jp");
        if (value != null && !value.isBlank()) {
            return value;
        }

        value = fallback.get(language);
        if (value != null && !value.isBlank()) {
            return value;
        }

        value = fallback.get("ja_jp");
        if (value != null && !value.isBlank()) {
            return value;
        }

        return fallbackText == null ? "" : fallbackText;
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

    private static boolean readBoolean(JsonObject object, String key, boolean fallback) {
        if (!object.has(key) || object.get(key).isJsonNull()) {
            return fallback;
        }
        return object.get(key).getAsBoolean();
    }

    public CompletableFuture<List<AudioFragmentResult>> resolveAudioFragments(SequenceTemplate template) {
        return resolveAudioFragments(template, AnnouncementResolveContext.empty());
    }

    public CompletableFuture<List<AudioFragmentResult>> resolveAudioFragments(
            SequenceTemplate template,
            AnnouncementResolveContext context
    ) {
        List<CompletableFuture<AudioFragmentResult>> futures = new ArrayList<>();

        for (SequenceItem item : template.items()) {
            switch (item.type()) {
                case PHRASE -> resolvePhrase(item.raw(), futures);
                case STATION -> resolveStation(item.raw(), context, futures);
                case LINE -> resolveLine(item.raw(), futures);
                case TRANSFER_LINES -> resolveTransferLines(context, futures);
                case DOOR_SIDE_PHRASE -> resolveDoorSidePhrase(context, futures);
                case PAUSE, SOUND, SUBTITLE, CONDITION, TTS_RAW -> {
                }
            }
        }

        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                .thenApply(ignored -> futures.stream().map(CompletableFuture::join).toList());
    }

    public CompletableFuture<ResolvedSequence> resolveSequence(SequenceTemplate template) {
        return resolveSequence(template, AnnouncementResolveContext.empty());
    }

    public CompletableFuture<ResolvedSequence> resolveSequence(
            SequenceTemplate template,
            AnnouncementResolveContext context
    ) {
        AnnouncementResolveContext safeContext = context == null
                ? AnnouncementResolveContext.empty()
                : context;

        List<CompletableFuture<List<ResolvedSequenceItem>>> futures = new ArrayList<>();

        for (SequenceItem item : template.items()) {
            futures.add(resolveItem(item, safeContext));
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

    private CompletableFuture<List<ResolvedSequenceItem>> resolveItem(
            SequenceItem item,
            AnnouncementResolveContext context
    ) {
        return switch (item.type()) {
            case PHRASE -> resolvePhraseItem(item.raw());
            case STATION -> resolveStationItem(item.raw(), context);
            case LINE -> resolveLineItem(item.raw());
            case TRANSFER_LINES -> resolveTransferLinesItem(item.raw(), context);
            case DOOR_SIDE_PHRASE -> resolveDoorSidePhraseItem(context);
            case PAUSE -> CompletableFuture.completedFuture(resolvePauseItem(item.raw()));
            case SUBTITLE -> CompletableFuture.completedFuture(resolveSubtitleItem(item.raw(), context));
            case SOUND -> CompletableFuture.completedFuture(resolveSoundItem(item.raw()));
            case CONDITION, TTS_RAW -> CompletableFuture.completedFuture(List.of());
        };
    }

    private CompletableFuture<List<ResolvedSequenceItem>> resolvePhraseItem(JsonObject raw) {
        String id = readString(raw, "id", "");
        return resolvePhraseId(id);
    }

    private CompletableFuture<List<ResolvedSequenceItem>> resolvePhraseId(String id) {
        if (id == null || id.isBlank()) {
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

        return audioCache.resolveOrGenerate(key).thenApply(this::toAudioItem);
    }

    private CompletableFuture<List<ResolvedSequenceItem>> resolveStationItem(
            JsonObject raw,
            AnnouncementResolveContext context
    ) {
        StationFragment fragment = stationFragment(raw, context);
        if (fragment == null) {
            return CompletableFuture.completedFuture(List.of());
        }

        AudioFragmentKey key = createKey(
                AudioFragmentType.STATION,
                fragment.id(),
                fragment.variant(),
                fragment.text(),
                false
        );

        return audioCache.resolveOrGenerate(key).thenApply(this::toAudioItem);
    }

    private CompletableFuture<List<ResolvedSequenceItem>> resolveLineItem(JsonObject raw) {
        String id = readString(raw, "id", "");
        String variant = readString(raw, "variant", "default");
        String text = readString(raw, "text", "");

        if (text.isBlank() && !id.isBlank()) {
            LineConfig line = StationLineConfigStore.line(id).orElse(null);
            if (line != null) {
                text = localized(line.getReading(), line.getDisplay(), id);
            }
        }

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

        return audioCache.resolveOrGenerate(key).thenApply(this::toAudioItem);
    }

    private CompletableFuture<List<ResolvedSequenceItem>> resolveTransferLinesItem(
            JsonObject raw,
            AnnouncementResolveContext context
    ) {
        if (context == null || context.transferLines().isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }

        int pauseMs = readInt(raw, "pause_ms_between", 180);
        boolean includeSuffix = readBoolean(raw, "include_suffix", true);

        List<CompletableFuture<List<ResolvedSequenceItem>>> futures = new ArrayList<>();

        for (int i = 0; i < context.transferLines().size(); i++) {
            LineConfig line = context.transferLines().get(i);
            String lineText = localized(line.getReading(), line.getDisplay(), line.getId());
            if (line.getId().isBlank() || lineText.isBlank()) {
                continue;
            }

            AudioFragmentKey key = createKey(
                    AudioFragmentType.LINE,
                    line.getId(),
                    "bare",
                    lineText,
                    false
            );

            CompletableFuture<List<ResolvedSequenceItem>> future = audioCache.resolveOrGenerate(key)
                    .thenApply(this::toAudioItem);

            futures.add(future);

            if (pauseMs > 0 && i < context.transferLines().size() - 1) {
                futures.add(CompletableFuture.completedFuture(List.of(ResolvedSequenceItem.pause(pauseMs))));
            }
        }

        if (includeSuffix) {
            futures.add(resolvePhraseId("common.transfer_suffix"));
        }

        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                .thenApply(ignored -> {
                    List<ResolvedSequenceItem> items = new ArrayList<>();
                    for (CompletableFuture<List<ResolvedSequenceItem>> future : futures) {
                        items.addAll(future.join());
                    }
                    return items;
                });
    }

    private CompletableFuture<List<ResolvedSequenceItem>> resolveDoorSidePhraseItem(
            AnnouncementResolveContext context
    ) {
        String phraseId = doorSidePhraseId(context);
        return resolvePhraseId(phraseId);
    }

    private List<ResolvedSequenceItem> resolvePauseItem(JsonObject raw) {
        int durationMs = readInt(raw, "duration_ms", 0);
        return List.of(ResolvedSequenceItem.pause(durationMs));
    }

    private List<ResolvedSequenceItem> resolveSubtitleItem(
            JsonObject raw,
            AnnouncementResolveContext context
    ) {
        String text = readString(raw, "text", "");
        if (text.isBlank()) {
            return List.of();
        }

        text = replaceContextVariables(text, context);

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

    private void resolveStation(
            JsonObject raw,
            AnnouncementResolveContext context,
            List<CompletableFuture<AudioFragmentResult>> futures
    ) {
        StationFragment fragment = stationFragment(raw, context);
        if (fragment == null) {
            return;
        }

        AudioFragmentKey key = createKey(
                AudioFragmentType.STATION,
                fragment.id(),
                fragment.variant(),
                fragment.text(),
                false
        );

        futures.add(audioCache.resolveOrGenerate(key));
    }

    private void resolveLine(JsonObject raw, List<CompletableFuture<AudioFragmentResult>> futures) {
        String id = readString(raw, "id", "");
        String variant = readString(raw, "variant", "default");
        String text = readString(raw, "text", "");

        if (text.isBlank() && !id.isBlank()) {
            LineConfig line = StationLineConfigStore.line(id).orElse(null);
            if (line != null) {
                text = localized(line.getReading(), line.getDisplay(), id);
            }
        }

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

    private void resolveTransferLines(
            AnnouncementResolveContext context,
            List<CompletableFuture<AudioFragmentResult>> futures
    ) {
        if (context == null || context.transferLines().isEmpty()) {
            return;
        }

        for (LineConfig line : context.transferLines()) {
            String text = localized(line.getReading(), line.getDisplay(), line.getId());
            if (line.getId().isBlank() || text.isBlank()) {
                continue;
            }

            AudioFragmentKey key = createKey(
                    AudioFragmentType.LINE,
                    line.getId(),
                    "bare",
                    text,
                    false
            );

            futures.add(audioCache.resolveOrGenerate(key));
        }
    }

    private void resolveDoorSidePhrase(
            AnnouncementResolveContext context,
            List<CompletableFuture<AudioFragmentResult>> futures
    ) {
        String phraseId = doorSidePhraseId(context);
        if (phraseId.isBlank()) {
            return;
        }

        PhraseEntry entry = VoicePackManager.phrases().get(phraseId).orElse(null);
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

    private StationFragment stationFragment(JsonObject raw, AnnouncementResolveContext context) {
        String id = readString(raw, "id", "");
        String variant = readString(raw, "variant", "default");
        String text = readString(raw, "text", "");

        if (text.isBlank() && context != null && context.station() != null) {
            StationConfig station = context.station();

            if (id.isBlank()) {
                id = station.getCustomId();
            }

            text = localized(station.getReading(), station.getDisplay(), station.getCustomId());

            if ("desu".equalsIgnoreCase(variant)) {
                text = text + "です";
            }
        }

        if (id.isBlank() || text.isBlank()) {
            return null;
        }

        return new StationFragment(id, variant, text);
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

    private record StationFragment(
            String id,
            String variant,
            String text
    ) {
    }
}