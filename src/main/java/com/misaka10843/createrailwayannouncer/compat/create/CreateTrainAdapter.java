package com.misaka10843.createrailwayannouncer.compat.create;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;
import java.util.UUID;

public interface CreateTrainAdapter {

    boolean isAvailable();


    Optional<CreateTrainSnapshot> findTrain(UUID trainId);


    Optional<CreateTrainSnapshot> findTrainContainingServerPlayer(
            ServerPlayer player
    );


    Optional<CreateTrainSnapshot> findTrainContainingClientPlayer(
            LocalPlayer player
    );


    Optional<CreateTrainSnapshot> findNearestTrain(
            ServerLevel level,
            double x,
            double y,
            double z,
            double maxDistance
    );
}