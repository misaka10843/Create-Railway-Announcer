package com.misaka10843.createrailwayannouncer.compat.create;

import com.misaka10843.createrailwayannouncer.config.StationConfig;
import com.misaka10843.createrailwayannouncer.config.StationLineConfigStore;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class CreateStationMatcher {

    private CreateStationMatcher() {
    }

    public static Optional<StationConfig> match(
            String filter
    ) {
        if (filter == null || filter.isBlank()) {
            return Optional.empty();
        }

        String target =
                normalize(filter);

        for (StationConfig station :
                StationLineConfigStore.stations().values()) {

            if (normalize(station.getCustomId())
                    .equals(target)) {
                return Optional.of(station);
            }

            if (containsValue(
                    station.getDisplay(),
                    target
            )) {
                return Optional.of(station);
            }

            if (containsValue(
                    station.getReading(),
                    target
            )) {
                return Optional.of(station);
            }
        }

        return Optional.empty();
    }


    private static boolean containsValue(
            Map<String, String> map,
            String target
    ) {
        for (String value : map.values()) {
            if (normalize(value).equals(target)) {
                return true;
            }
        }

        return false;
    }


    private static String normalize(
            String value
    ) {
        return value
                .toLowerCase(Locale.ROOT)
                .replace("*", "")
                .trim();
    }
}