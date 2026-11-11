package com.misaka10843.create_railway_announcer.config;

import com.misaka10843.create_railway_signage.data.RailwaySignageSavedData;
import com.misaka10843.create_railway_signage.model.RailwayLine;
import com.misaka10843.create_railway_signage.model.StationGroup;
import net.minecraft.server.MinecraftServer;
import java.util.*;

public final class StationLineConfigStore {
    private static volatile Map<String, StationConfig> stations = Map.of();
    private static volatile Map<String, LineConfig> lines = Map.of();
    private static MinecraftServer owner;
    private static long revision = -1;
    private static long audioRevision = -1;

    private StationLineConfigStore() {}

    public static void clear() {
        stations = Map.of();
        lines = Map.of();
        owner = null;
        revision = -1;
        audioRevision = -1;
    }

    public static void refresh(MinecraftServer server) {
        var data = RailwaySignageSavedData.get(server);
        var audio = com.misaka10843.create_railway_announcer.data.AnnouncementSavedData.get(server);
        if (owner == server && revision == data.revision() && audioRevision == audio.revision()) return;
        owner = server;
        revision = data.revision();
        audioRevision=audio.revision();
        apply(data.stationGroups(), data.lines(),audio.stations());
    }

    public static void apply(Collection<StationGroup> groups, Collection<RailwayLine> railwayLines) {
        apply(groups,railwayLines,AnnouncementClientData.profiles());
    }
    public static void apply(Collection<StationGroup> groups, Collection<RailwayLine> railwayLines,Map<String,com.misaka10843.create_railway_announcer.model.StationAnnouncementSettings> profiles) {
        Map<String, LineConfig> nextLines = new LinkedHashMap<>();
        for (var line : railwayLines) {
            var value = new LineConfig();
            value.setId(line.id());
            names(value.getDisplay(), line.japaneseName(), line.romanizedName(), line.chineseName());
            nextLines.put(line.id(), value);
        }
        Map<String, StationConfig> nextStations = new LinkedHashMap<>();
        for (var group : groups) {
            var profile=profiles.getOrDefault(group.id(),com.misaka10843.create_railway_announcer.model.StationAnnouncementSettings.DEFAULT);
            for (var binding : group.platforms()) {
                var value = new StationConfig();
                value.setAnnouncements(profile);
                value.setEnabled(profile.enabled());
                value.setCustomId(group.id() + "/" + binding.platformId());
                value.setCreateStationId(binding.createStationId());
                names(value.getDisplay(), group.japaneseName(), group.romanizedName(), group.chineseName());
                value.getReading().put("ja_jp", profile.reading().isBlank() ? group.japaneseName() : profile.reading());
                value.getReading().put("en_us",group.romanizedName());
                if(!group.chineseName().isBlank())value.getReading().put("zh_cn",group.chineseName());
                value.setPosition(binding.stationBlockPos());
                value.setDimension(binding.dimension().location().toString());
                value.setPlatform(binding.platformId());
                value.setDoorSide(profile.door(binding.platformId()));
                for (var platform : group.platforms()) {
                    if (!platform.lineId().isBlank() && !platform.lineId().equals(binding.lineId())
                            && !value.getTransferLineIds().contains(platform.lineId())) value.getTransferLineIds().add(platform.lineId());
                }
                nextStations.put(value.getCustomId(), value);
            }
        }
        lines = Collections.unmodifiableMap(nextLines);
        stations = Collections.unmodifiableMap(nextStations);
    }

    private static void names(Map<String, String> target, String japanese, String romanized, String chinese) {
        target.put("ja_jp", japanese);
        target.put("en_us", romanized);
        if (!chinese.isBlank()) target.put("zh_cn", chinese);
    }

    public static Optional<StationConfig> station(String id) { return Optional.ofNullable(stations.get(id)); }
    public static Optional<LineConfig> line(String id) { return Optional.ofNullable(lines.get(id)); }
    public static Map<String, StationConfig> stations() { return stations; }
    public static Map<String, LineConfig> lines() { return lines; }
}
