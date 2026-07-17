package com.misaka10843.createrailwayannouncer.compat.create;

public record CreateScheduleSnapshot(
        String nextStationFilter,
        String destinationFilter,
        int currentEntry
) {
}