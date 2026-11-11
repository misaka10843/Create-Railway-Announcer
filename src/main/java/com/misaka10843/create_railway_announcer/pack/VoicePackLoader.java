package com.misaka10843.create_railway_announcer.pack;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.misaka10843.create_railway_announcer.CreateRailwayAnnouncer;
import com.misaka10843.create_railway_announcer.audio.AudioChannel;
import com.misaka10843.create_railway_announcer.sequence.SequenceItem;
import com.misaka10843.create_railway_announcer.sequence.SequenceItemType;
import com.misaka10843.create_railway_announcer.sequence.SequenceTemplate;
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
        try {
            String id="jr_like_default",language="ja_jp";
            try{id=com.misaka10843.create_railway_announcer.config.ClientConfig.VOICE_PACK.get();language=com.misaka10843.create_railway_announcer.config.ClientConfig.TTS_LANGUAGE.get();}catch(IllegalStateException ignored){}
            reloadPack(id,language);
        } catch (Exception e) {
            CreateRailwayAnnouncer.LOGGER.error("Failed to load default voice pack", e);
        }
    }

    public static synchronized void reloadPack(String id,String language) throws IOException {
        Path bundled=ensureDefaultPackExtracted();mergeMissingTemplates(bundled);
        if(!id.matches("[a-z0-9_-]{1,64}")||!language.matches("[a-z]{2}_[a-z]{2}"))throw new IOException("Invalid pack or language ID");
        var root=bundled.getParent().resolve(id);var pack=loadPack(root);
        if(!Files.isRegularFile(root.resolve("templates/"+language+".json"))||!Files.isRegularFile(root.resolve("phrases/"+language+".json")))language=pack.defaultLanguage();
        var old=VoicePackManager.activePack().orElse(null);var oldPhrases=java.util.Map.copyOf(VoicePackManager.phrases().all());var oldTemplates=java.util.Map.copyOf(VoicePackManager.sequences().all());
        VoicePackManager.clear();
        try{loadPhrases(pack,language);loadTemplates(pack,language);VoicePackManager.setActivePack(pack);}
        catch(Exception error){VoicePackManager.clear();oldPhrases.values().forEach(VoicePackManager.phrases()::put);oldTemplates.values().forEach(VoicePackManager.sequences()::put);VoicePackManager.setActivePack(old);throw new IOException("Cannot load voice pack "+id,error);}
    }
    public static List<String> availablePacks(){
        try{var root=ensureDefaultPackExtracted().getParent();try(var paths=Files.list(root)){return paths.filter(Files::isDirectory).filter(p->p.getFileName().toString().matches("[a-z0-9_-]{1,64}")&&Files.isRegularFile(p.resolve("pack.json"))).map(p->p.getFileName().toString()).sorted().toList();}}
        catch(IOException error){return List.of(DEFAULT_PACK_ID);}
    }

    private static void mergeMissingTemplates(Path root) throws IOException {
        for(String language:List.of("ja_jp","en_us","zh_cn"))for(String kind:List.of("templates","phrases"))mergeBundled(root,kind+"/"+language+".json");
    }
    private static void mergeBundled(Path root,String relative) throws IOException {
        Path target=root.resolve(relative),baseline=root.resolve(".bundled").resolve(relative);
        String resource="/assets/"+CreateRailwayAnnouncer.MODID+"/default_packs/"+DEFAULT_PACK_ID+"/"+relative;
        JsonObject bundled;try(var stream=java.util.Objects.requireNonNull(VoicePackLoader.class.getResourceAsStream(resource))){bundled=JsonParser.parseReader(new java.io.InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();}
        JsonObject local=Files.exists(target)?JsonParser.parseString(Files.readString(target)).getAsJsonObject():new JsonObject();
        JsonObject previous=Files.exists(baseline)?JsonParser.parseString(Files.readString(baseline)).getAsJsonObject():new JsonObject();
        for(var entry:bundled.entrySet())if(!local.has(entry.getKey())||java.util.Objects.equals(local.get(entry.getKey()),previous.get(entry.getKey())))local.add(entry.getKey(),entry.getValue());
        writeJson(target,local);writeJson(baseline,bundled);
    }
    private static void writeJson(Path target,JsonObject value) throws IOException {
        Files.createDirectories(target.getParent());Path temporary=Files.createTempFile(target.getParent(),"pack-",".json");
        try{Files.writeString(temporary,new com.google.gson.GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(value),StandardCharsets.UTF_8);Files.move(temporary,target,java.nio.file.StandardCopyOption.REPLACE_EXISTING);}
        finally{Files.deleteIfExists(temporary);}
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

        copyResourceIfMissing("/assets/" + CreateRailwayAnnouncer.MODID + "/default_packs/" + DEFAULT_PACK_ID + "/door_bip.ogg",
                targetRoot.resolve("sounds/door_bip.ogg"));
        copyResourceIfMissing("/assets/" + CreateRailwayAnnouncer.MODID + "/default_packs/" + DEFAULT_PACK_ID + "/departure.ogg",targetRoot.resolve("sounds/departure.ogg"));
        for(String language:List.of("en_us","zh_cn"))for(String kind:List.of("templates","phrases"))copyResourceIfMissing("/assets/"+CreateRailwayAnnouncer.MODID+"/default_packs/"+DEFAULT_PACK_ID+"/"+kind+"/"+language+".json",targetRoot.resolve(kind+"/"+language+".json"));
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
                    throw new IOException("Unknown channel in voice pack template: "+id,e);
                }
                var eventType=com.misaka10843.create_railway_announcer.announcement.AnnouncementEventType.valueOf(event.toUpperCase(java.util.Locale.ROOT));
                if(channel!=com.misaka10843.create_railway_announcer.announcement.AnnouncementSequenceMapper.channel(eventType))throw new IOException("Event/channel mismatch in voice pack: "+id);

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
