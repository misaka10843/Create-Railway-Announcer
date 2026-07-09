package com.misaka10843.createrailwayannouncer.announcement;

import com.misaka10843.createrailwayannouncer.audio.AudioChannel;
import net.minecraft.core.BlockPos;

import java.util.UUID;

public record ServerAnnouncementRequest(
        AnnouncementEventType eventType,
        UUID trainId,
        String trainName,
        UUID stationId,
        BlockPos stationPos,
        String currentStationConfigId,
        String nextStationConfigId,
        String destinationStationConfigId,
        int horizontalRange,
        int verticalRange,
        AudioChannel channel,
        int priority
) {
    public static ServerAnnouncementRequest of(
            AnnouncementEventType eventType,
            UUID trainId,
            String trainName,
            UUID stationId,
            BlockPos stationPos,
            String currentStationConfigId,
            String nextStationConfigId,
            String destinationStationConfigId,
            AudioChannel channel,
            int priority
    ) {
        return new ServerAnnouncementRequest(
                eventType,
                trainId,
                trainName,
                stationId,
                stationPos,
                currentStationConfigId,
                nextStationConfigId,
                destinationStationConfigId,
                -1,
                -1,
                channel,
                priority
        );
    }

    public ServerAnnouncementRequest withRange(int horizontalRange, int verticalRange) {
        return new ServerAnnouncementRequest(
                eventType,
                trainId,
                trainName,
                stationId,
                stationPos,
                currentStationConfigId,
                nextStationConfigId,
                destinationStationConfigId,
                horizontalRange,
                verticalRange,
                channel,
                priority
        );
    }
}