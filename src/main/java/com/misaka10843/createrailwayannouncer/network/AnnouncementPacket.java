package com.misaka10843.createrailwayannouncer.network;

import com.misaka10843.createrailwayannouncer.announcement.AnnouncementEventType;
import com.misaka10843.createrailwayannouncer.audio.AudioChannel;
import net.minecraft.core.BlockPos;

import java.util.UUID;

public record AnnouncementPacket(
        UUID announcementId,
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
        int priority,
        long serverGameTime
) {
}
