package com.misaka10843.create_railway_announcer.compat.create;

public record CreateScheduleSnapshot(
        String nextStationFilter,
        String destinationFilter,
        int currentEntry
) {
}