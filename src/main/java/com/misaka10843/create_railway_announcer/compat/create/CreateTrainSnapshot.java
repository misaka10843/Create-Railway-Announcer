package com.misaka10843.create_railway_announcer.compat.create;

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
    public CreateTrainSnapshot at(BlockPos pos) {
        return new CreateTrainSnapshot(trainId,trainName,pos,currentStation,currentPlatform,nextStation,nextPlatform,destinationStation,moving,speed);
    }
}
