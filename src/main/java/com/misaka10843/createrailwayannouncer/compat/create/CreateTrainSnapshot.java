package com.misaka10843.createrailwayannouncer.compat.create;

import net.minecraft.core.BlockPos;

import java.util.UUID;

public record CreateTrainSnapshot(
        UUID trainId,
        String trainName,

        BlockPos position,

        CreateStationSnapshot currentStation,
        CreatePlatformSnapshot currentPlatform,
        CreateStationSnapshot nextStation,
        CreatePlatformSnapshot nextPlatform,
        CreateStationSnapshot destinationStation,

        boolean moving,
        double speed
) {
}