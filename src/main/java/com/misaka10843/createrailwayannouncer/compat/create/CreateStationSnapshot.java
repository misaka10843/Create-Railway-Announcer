package com.misaka10843.createrailwayannouncer.compat.create;

import net.minecraft.core.BlockPos;

import java.util.UUID;

public record CreateStationSnapshot(
        UUID stationId,
        String name,
        BlockPos position
) {
}