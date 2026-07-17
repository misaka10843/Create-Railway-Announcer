package com.misaka10843.createrailwayannouncer.announcement.source;

import com.misaka10843.createrailwayannouncer.audio.AudioChannel;
import net.minecraft.core.BlockPos;

import java.util.UUID;

public record AnnouncementSource(
        UUID sourceId,
        String levelId,
        AnnouncementSourceType type,
        UUID trainId,
        String stationConfigId,
        String platformId,
        BlockPos position,
        int horizontalRange,
        int verticalRange,
        AudioChannel channel
) {
}