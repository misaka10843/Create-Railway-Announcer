package com.misaka10843.create_railway_announcer.compat.create;

import com.misaka10843.create_railway_announcer.config.StationConfig;
import com.misaka10843.create_railway_announcer.config.StationLineConfigStore;
import com.misaka10843.create_railway_signage.data.RailwaySignageSavedData;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import java.util.Optional;
import java.util.regex.Pattern;

public final class CreateStationMatcher {
    private CreateStationMatcher() {}
    public static Optional<StationConfig> match(String filter) {
        if (filter == null || filter.isBlank()) return Optional.empty();
        var exact = StationLineConfigStore.station(filter);
        if (exact.isPresent()) return exact;
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return Optional.empty();
        Pattern pattern = Pattern.compile("\\Q" + filter.replace("*", "\\E.*\\Q") + "\\E");
        var matches = RailwaySignageSavedData.get(server).stationGroups().stream()
                .flatMap(group -> group.platforms().stream().map(binding -> java.util.Map.entry(group, binding)))
                .filter(entry -> pattern.matcher(entry.getValue().createStationName()).matches())
                .map(entry -> entry.getKey().id() + "/" + entry.getValue().platformId()).distinct().toList();
        return matches.size() == 1 ? StationLineConfigStore.station(matches.getFirst()) : Optional.empty();
    }
}
