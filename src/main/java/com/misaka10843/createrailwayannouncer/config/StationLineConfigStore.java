package com.misaka10843.createrailwayannouncer.config;

import com.google.gson.*;
import com.misaka10843.createrailwayannouncer.CreateRailwayAnnouncer;
import net.minecraft.core.BlockPos;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public final class StationLineConfigStore {
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    private static final Map<String, StationConfig> STATIONS = new LinkedHashMap<>();
    private static final Map<String, LineConfig> LINES = new LinkedHashMap<>();

    private StationLineConfigStore() {
    }

    public static synchronized void reload() {
        try {
            Path root = root();
            Files.createDirectories(root);

            Path stationsFile = root.resolve("stations.json");
            Path linesFile = root.resolve("lines.json");

            ensureSampleStations(stationsFile);
            ensureSampleLines(linesFile);

            STATIONS.clear();
            LINES.clear();

            loadStations(stationsFile);
            loadLines(linesFile);

            CreateRailwayAnnouncer.LOGGER.info(
                    "Loaded Create Railway Announcer station data: stations={}, lines={}",
                    STATIONS.size(),
                    LINES.size()
            );
        } catch (Exception e) {
            CreateRailwayAnnouncer.LOGGER.error("Failed to reload station/line config store", e);
        }
    }

    public static synchronized Optional<StationConfig> station(String id) {
        if (STATIONS.isEmpty() && LINES.isEmpty()) {
            reload();
        }

        if (id == null || id.isBlank()) {
            return Optional.empty();
        }

        return Optional.ofNullable(STATIONS.get(id));
    }

    public static synchronized Optional<LineConfig> line(String id) {
        if (STATIONS.isEmpty() && LINES.isEmpty()) {
            reload();
        }

        if (id == null || id.isBlank()) {
            return Optional.empty();
        }

        return Optional.ofNullable(LINES.get(id));
    }

    public static synchronized Map<String, StationConfig> stations() {
        if (STATIONS.isEmpty() && LINES.isEmpty()) {
            reload();
        }

        return Collections.unmodifiableMap(new LinkedHashMap<>(STATIONS));
    }

    public static synchronized Map<String, LineConfig> lines() {
        if (STATIONS.isEmpty() && LINES.isEmpty()) {
            reload();
        }

        return Collections.unmodifiableMap(new LinkedHashMap<>(LINES));
    }

    public static synchronized boolean bindStationPosition(
            String stationId,
            String dimension,
            BlockPos position
    ) {
        if (stationId == null || stationId.isBlank()) {
            return false;
        }

        if (dimension == null || dimension.isBlank() || position == null) {
            return false;
        }

        try {
            Path file = root().resolve("stations.json");
            ensureSampleStations(file);

            JsonObject root = readObject(file);
            JsonObject stationObject = findStationObject(root, stationId);

            if (stationObject == null) {
                return false;
            }

            JsonObject positionObject = new JsonObject();
            positionObject.addProperty("dimension", dimension);
            positionObject.addProperty("x", position.getX());
            positionObject.addProperty("y", position.getY());
            positionObject.addProperty("z", position.getZ());

            stationObject.add("position", positionObject);

            writeJson(file, root);
            reload();

            return true;
        } catch (Exception e) {
            CreateRailwayAnnouncer.LOGGER.error("Failed to bind station position: {}", stationId, e);
            return false;
        }
    }

    public static synchronized boolean unbindStationPosition(String stationId) {
        if (stationId == null || stationId.isBlank()) {
            return false;
        }

        try {
            Path file = root().resolve("stations.json");
            ensureSampleStations(file);

            JsonObject root = readObject(file);
            JsonObject stationObject = findStationObject(root, stationId);

            if (stationObject == null) {
                return false;
            }

            stationObject.remove("position");

            writeJson(file, root);
            reload();

            return true;
        } catch (Exception e) {
            CreateRailwayAnnouncer.LOGGER.error("Failed to unbind station position: {}", stationId, e);
            return false;
        }
    }

    private static JsonObject findStationObject(JsonObject root, String stationId) {
        if (root.has(stationId) && root.get(stationId).isJsonObject()) {
            return root.getAsJsonObject(stationId);
        }

        for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
            if (!entry.getValue().isJsonObject()) {
                continue;
            }

            JsonObject object = entry.getValue().getAsJsonObject();
            String customId = readString(object, "custom_id", entry.getKey());

            if (stationId.equals(customId)) {
                return object;
            }
        }

        return null;
    }

    public static Path root() {
        return FMLPaths.CONFIGDIR.get()
                .resolve(CreateRailwayAnnouncer.MODID)
                .resolve("server_data");
    }

    private static void loadStations(Path file) throws IOException {
        JsonObject root = readObject(file);

        for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
            if (!entry.getValue().isJsonObject()) {
                continue;
            }

            StationConfig config = parseStation(entry.getKey(), entry.getValue().getAsJsonObject());
            if (config.isEnabled() && !config.getCustomId().isBlank()) {
                STATIONS.put(config.getCustomId(), config);
            }
        }
    }

    private static void loadLines(Path file) throws IOException {
        JsonObject root = readObject(file);

        for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
            if (!entry.getValue().isJsonObject()) {
                continue;
            }

            LineConfig config = parseLine(entry.getKey(), entry.getValue().getAsJsonObject());
            if (!config.getId().isBlank()) {
                LINES.put(config.getId(), config);
            }
        }
    }

    private static StationConfig parseStation(String fallbackId, JsonObject object) {
        StationConfig config = new StationConfig();

        String customId = readString(object, "custom_id", fallbackId);
        config.setCustomId(customId);

        config.setEnabled(readBoolean(object, "enabled", true));

        String uuid = readString(object, "create_station_id", "");
        if (!uuid.isBlank()) {
            try {
                config.setCreateStationId(UUID.fromString(uuid));
            } catch (IllegalArgumentException ignored) {
            }
        }

        readStringMap(object, "display", config.getDisplay());
        readStringMap(object, "reading", config.getReading());

        String doorSide = readString(object, "door_side", "none");
        try {
            config.setDoorSide(DoorSide.valueOf(doorSide.trim().toUpperCase()));
        } catch (IllegalArgumentException ignored) {
            config.setDoorSide(DoorSide.NONE);
        }

        config.setPlatform(readString(object, "platform", "1"));
        config.setHorizontalRange(readInt(object, "horizontal_range", ServerConfig.DEFAULT_STATION_HORIZONTAL_RANGE.get()));
        config.setVerticalRange(readInt(object, "vertical_range", ServerConfig.DEFAULT_STATION_VERTICAL_RANGE.get()));
        if (object.has("position") && object.get("position").isJsonObject()) {
            JsonObject positionObject = object.getAsJsonObject("position");

            String dimension = readString(positionObject, "dimension", "");
            int x = readInt(positionObject, "x", 0);
            int y = readInt(positionObject, "y", 0);
            int z = readInt(positionObject, "z", 0);

            if (!dimension.isBlank()) {
                config.setDimension(dimension);
                config.setPosition(new BlockPos(x, y, z));
            }
        }

        if (object.has("transfer_line_ids") && object.get("transfer_line_ids").isJsonArray()) {
            for (JsonElement element : object.getAsJsonArray("transfer_line_ids")) {
                if (!element.isJsonPrimitive()) {
                    continue;
                }

                String id = element.getAsString();
                if (id != null && !id.isBlank()) {
                    config.getTransferLineIds().add(id);
                }
            }
        }

        return config;
    }

    private static LineConfig parseLine(String fallbackId, JsonObject object) {
        LineConfig config = new LineConfig();

        config.setId(readString(object, "id", fallbackId));
        readStringMap(object, "display", config.getDisplay());
        readStringMap(object, "reading", config.getReading());
        readStringMap(object, "short_name", config.getShortName());

        return config;
    }

    private static JsonObject readObject(Path file) throws IOException {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement element = JsonParser.parseReader(reader);
            if (element == null || !element.isJsonObject()) {
                return new JsonObject();
            }

            return element.getAsJsonObject();
        }
    }

    private static void readStringMap(JsonObject object, String key, Map<String, String> target) {
        if (!object.has(key) || !object.get(key).isJsonObject()) {
            return;
        }

        JsonObject map = object.getAsJsonObject(key);
        for (Map.Entry<String, JsonElement> entry : map.entrySet()) {
            if (!entry.getValue().isJsonPrimitive()) {
                continue;
            }

            target.put(entry.getKey(), entry.getValue().getAsString());
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

        try {
            return object.get(key).getAsInt();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static boolean readBoolean(JsonObject object, String key, boolean fallback) {
        if (!object.has(key) || object.get(key).isJsonNull()) {
            return fallback;
        }

        try {
            return object.get(key).getAsBoolean();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static void ensureSampleStations(Path file) throws IOException {
        if (Files.isRegularFile(file)) {
            return;
        }

        JsonObject root = new JsonObject();

        JsonObject yurakucho = new JsonObject();
        yurakucho.addProperty("custom_id", "yurakucho");
        yurakucho.addProperty("enabled", true);

        JsonObject display = new JsonObject();
        display.addProperty("ja_jp", "有楽町");
        display.addProperty("en_us", "Yurakucho");
        display.addProperty("zh_cn", "有乐町");
        yurakucho.add("display", display);

        JsonObject reading = new JsonObject();
        reading.addProperty("ja_jp", "有楽町");
        reading.addProperty("en_us", "Yurakucho");
        yurakucho.add("reading", reading);

        yurakucho.addProperty("door_side", "left");
        yurakucho.addProperty("platform", "1");
        yurakucho.addProperty("horizontal_range", 64);
        yurakucho.addProperty("vertical_range", 24);

        com.google.gson.JsonArray transferLineIds = new com.google.gson.JsonArray();
        transferLineIds.add("tokyo_metro_hibiya");
        transferLineIds.add("tokyo_metro_yurakucho");
        yurakucho.add("transfer_line_ids", transferLineIds);

        root.add("yurakucho", yurakucho);

        writeJson(file, root);
    }

    private static void ensureSampleLines(Path file) throws IOException {
        if (Files.isRegularFile(file)) {
            return;
        }

        JsonObject root = new JsonObject();

        JsonObject hibiya = new JsonObject();
        hibiya.addProperty("id", "tokyo_metro_hibiya");

        JsonObject hibiyaDisplay = new JsonObject();
        hibiyaDisplay.addProperty("ja_jp", "地下鉄日比谷線");
        hibiyaDisplay.addProperty("en_us", "Tokyo Metro Hibiya Line");
        hibiyaDisplay.addProperty("zh_cn", "东京地铁日比谷线");
        hibiya.add("display", hibiyaDisplay);

        JsonObject hibiyaReading = new JsonObject();
        hibiyaReading.addProperty("ja_jp", "地下鉄日比谷線");
        hibiyaReading.addProperty("en_us", "Tokyo Metro Hibiya Line");
        hibiya.add("reading", hibiyaReading);

        JsonObject hibiyaShort = new JsonObject();
        hibiyaShort.addProperty("ja_jp", "日比谷線");
        hibiya.add("short_name", hibiyaShort);

        root.add("tokyo_metro_hibiya", hibiya);

        JsonObject yurakucho = new JsonObject();
        yurakucho.addProperty("id", "tokyo_metro_yurakucho");

        JsonObject yurakuchoDisplay = new JsonObject();
        yurakuchoDisplay.addProperty("ja_jp", "地下鉄有楽町線");
        yurakuchoDisplay.addProperty("en_us", "Tokyo Metro Yurakucho Line");
        yurakuchoDisplay.addProperty("zh_cn", "东京地铁有乐町线");
        yurakucho.add("display", yurakuchoDisplay);

        JsonObject yurakuchoReading = new JsonObject();
        yurakuchoReading.addProperty("ja_jp", "地下鉄有楽町線");
        yurakuchoReading.addProperty("en_us", "Tokyo Metro Yurakucho Line");
        yurakucho.add("reading", yurakuchoReading);

        JsonObject yurakuchoShort = new JsonObject();
        yurakuchoShort.addProperty("ja_jp", "有楽町線");
        yurakucho.add("short_name", yurakuchoShort);

        root.add("tokyo_metro_yurakucho", yurakucho);

        writeJson(file, root);
    }

    private static void writeJson(Path file, JsonObject object) throws IOException {
        Files.createDirectories(file.getParent());

        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            GSON.toJson(object, writer);
        }
    }
}