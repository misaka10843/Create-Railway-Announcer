package com.misaka10843.createrailwayannouncer.pack;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.misaka10843.createrailwayannouncer.CreateRailwayAnnouncer;
import com.misaka10843.createrailwayannouncer.audio.AudioChannel;
import com.misaka10843.createrailwayannouncer.sequence.SequenceItem;
import com.misaka10843.createrailwayannouncer.sequence.SequenceItemType;
import com.misaka10843.createrailwayannouncer.sequence.SequenceTemplate;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class VoicePackLoader {
    private static final String DEFAULT_PACK_ID = "jr_like_default";

    private VoicePackLoader() {
    }

    public static void reloadDefaultPack() {
        VoicePackManager.clear();

        try {
            Path packRoot = ensureDefaultPackExtracted();
            VoicePack pack = loadPack(packRoot);
            loadPhrases(pack, pack.defaultLanguage());
            loadTemplates(pack, pack.defaultLanguage());

            VoicePackManager.setActivePack(pack);
            CreateRailwayAnnouncer.LOGGER.info(
                    "Loaded voice pack {} ({}) with {} phrases and {} sequences",
                    pack.id(),
                    pack.version(),
                    VoicePackManager.phrases().size(),
                    VoicePackManager.sequences().size()
            );
        } catch (Exception e) {
            CreateRailwayAnnouncer.LOGGER.error("Failed to load default voice pack", e);
        }
    }

    public static Path ensureDefaultPackExtracted() throws IOException {
        Path packsRoot = FMLPaths.CONFIGDIR.get()
                .resolve(CreateRailwayAnnouncer.MODID)
                .resolve("packs");

        Path targetRoot = packsRoot.resolve(DEFAULT_PACK_ID);

        copyResourceIfMissing(
                "/assets/" + CreateRailwayAnnouncer.MODID + "/default_packs/" + DEFAULT_PACK_ID + "/pack.json",
                targetRoot.resolve("pack.json")
        );

        copyResourceIfMissing(
                "/assets/" + CreateRailwayAnnouncer.MODID + "/default_packs/" + DEFAULT_PACK_ID + "/phrases/ja_jp.json",
                targetRoot.resolve("phrases").resolve("ja_jp.json")
        );

        copyResourceIfMissing(
                "/assets/" + CreateRailwayAnnouncer.MODID + "/default_packs/" + DEFAULT_PACK_ID + "/templates/ja_jp.json",
                targetRoot.resolve("templates").resolve("ja_jp.json")
        );

        return targetRoot;
    }

    public static void loadTemplates(VoicePack pack, String language) throws IOException {
        Path templatesPath = pack.root()
                .resolve("templates")
                .resolve(language + ".json");

        if (!Files.isRegularFile(templatesPath)) {
            CreateRailwayAnnouncer.LOGGER.warn("Voice pack {} has no template file for language {}", pack.id(), language);
            return;
        }

        try (Reader reader = Files.newBufferedReader(templatesPath, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();

            for (String id : root.keySet()) {
                JsonObject object = root.getAsJsonObject(id);

                String event = readString(object, "event", id);
                String channelName = readString(object, "channel", "ONBOARD_VOICE");
                int priority = readInt(object, "priority", 50);

                AudioChannel channel;
                try {
                    channel = AudioChannel.valueOf(channelName.toUpperCase());
                } catch (IllegalArgumentException e) {
                    CreateRailwayAnnouncer.LOGGER.warn("Unknown audio channel {} in sequence {}, fallback to ONBOARD_VOICE", channelName, id);
                    channel = AudioChannel.ONBOARD_VOICE;
                }

                List<SequenceItem> items = new ArrayList<>();
                if (object.has("sequence") && object.get("sequence").isJsonArray()) {
                    for (JsonElement element : object.getAsJsonArray("sequence")) {
                        if (!element.isJsonObject()) {
                            continue;
                        }

                        JsonObject itemObject = element.getAsJsonObject();
                        String typeName = readString(itemObject, "type", "");

                        try {
                            SequenceItemType type = SequenceItemType.fromString(typeName);
                            items.add(new SequenceItem(type, itemObject));
                        } catch (IllegalArgumentException ex) {
                            CreateRailwayAnnouncer.LOGGER.warn("Skipping invalid sequence item in {}: {}", id, ex.getMessage());
                        }
                    }
                }

                VoicePackManager.sequences().put(new SequenceTemplate(
                        id,
                        event,
                        channel,
                        priority,
                        items
                ));
            }
        }
    }

    public static VoicePack loadPack(Path root) throws IOException {
        Path packJson = root.resolve("pack.json");
        try (Reader reader = Files.newBufferedReader(packJson, StandardCharsets.UTF_8)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();

            int packFormat = readInt(json, "pack_format", 1);
            String id = readString(json, "id", root.getFileName().toString());
            String name = readString(json, "name", id);
            String description = readString(json, "description", "");
            String author = readString(json, "author", "");
            String version = readString(json, "version", "0.0.0");
            String defaultLanguage = readString(json, "default_language", "ja_jp");

            List<String> supportedLanguages = new ArrayList<>();
            if (json.has("supported_languages") && json.get("supported_languages").isJsonArray()) {
                for (JsonElement element : json.getAsJsonArray("supported_languages")) {
                    supportedLanguages.add(element.getAsString());
                }
            }
            if (supportedLanguages.isEmpty()) {
                supportedLanguages.add(defaultLanguage);
            }

            return new VoicePack(
                    packFormat,
                    id,
                    name,
                    description,
                    author,
                    version,
                    defaultLanguage,
                    supportedLanguages,
                    root
            );
        }
    }

    public static void loadPhrases(VoicePack pack, String language) throws IOException {
        Path phrasesPath = pack.root()
                .resolve("phrases")
                .resolve(language + ".json");

        try (Reader reader = Files.newBufferedReader(phrasesPath, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();

            for (String id : root.keySet()) {
                JsonElement element = root.get(id);

                if (element.isJsonPrimitive()) {
                    VoicePackManager.phrases().put(new PhraseEntry(id, element.getAsString(), false));
                    continue;
                }

                if (element.isJsonObject()) {
                    JsonObject object = element.getAsJsonObject();
                    String text = readString(object, "text", "");
                    boolean ssml = readBoolean(object, "ssml", false);

                    if (!text.isBlank()) {
                        VoicePackManager.phrases().put(new PhraseEntry(id, text, ssml));
                    }
                }
            }
        }
    }

    private static void copyResourceIfMissing(String resourcePath, Path target) throws IOException {
        if (Files.isRegularFile(target)) {
            return;
        }

        Files.createDirectories(target.getParent());

        try (InputStream inputStream = VoicePackLoader.class.getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new IOException("Missing bundled voice pack resource: " + resourcePath);
            }

            Files.copy(inputStream, target);
            CreateRailwayAnnouncer.LOGGER.info("Extracted default voice pack resource to {}", target);
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
}