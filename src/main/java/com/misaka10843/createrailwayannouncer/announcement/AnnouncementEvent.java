package com.misaka10843.createrailwayannouncer.announcement;

import com.misaka10843.createrailwayannouncer.audio.AudioChannel;
import net.minecraft.core.BlockPos;

import java.util.UUID;

public record AnnouncementEvent(
        UUID announcementId,
        AnnouncementEventType type,
        UUID trainId,
        String trainName,
        UUID stationId,
        BlockPos stationPos,
        String currentStationConfigId,
        String nextStationConfigId,
        String destinationStationConfigId,
        AudioChannel channel,
        int priority,
        long gameTime
) {
}
