package com.misaka10843.createrailwayannouncer.sequence;

import com.misaka10843.createrailwayannouncer.config.LineConfig;
import com.misaka10843.createrailwayannouncer.config.StationConfig;
import com.misaka10843.createrailwayannouncer.config.StationLineConfigStore;

import java.util.ArrayList;
import java.util.List;

public record AnnouncementResolveContext(
        StationConfig station,
        List<LineConfig> transferLines
) {
    public static AnnouncementResolveContext empty() {
        return new AnnouncementResolveContext(null, List.of());
    }

    public static AnnouncementResolveContext forStationId(String stationId) {
        if (stationId == null || stationId.isBlank()) {
            return empty();
        }

        StationConfig station = StationLineConfigStore.station(stationId).orElse(null);
        if (station == null) {
            return empty();
        }

        List<LineConfig> transferLines = new ArrayList<>();
        for (String lineId : station.getTransferLineIds()) {
            StationLineConfigStore.line(lineId).ifPresent(transferLines::add);
        }

        return new AnnouncementResolveContext(station, List.copyOf(transferLines));
    }

    public boolean hasStation() {
        return station != null;
    }
}