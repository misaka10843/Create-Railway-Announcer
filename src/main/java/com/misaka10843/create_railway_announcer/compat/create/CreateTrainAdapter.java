package com.misaka10843.create_railway_announcer.compat.create;

import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;
import java.util.UUID;

public interface CreateTrainAdapter {

    boolean isAvailable();

    default Optional<UUID> ridingTrainId(Player player) { return Optional.empty(); }


    Optional<CreateTrainSnapshot> findTrain(UUID trainId);


    Optional<CreateTrainSnapshot> findTrainContainingServerPlayer(
            ServerPlayer player
    );


    Optional<CreateTrainSnapshot> findTrainContainingClientPlayer(
            Player player
    );


    Optional<CreateTrainSnapshot> findNearestTrain(
            ServerLevel level,
            double x,
            double y,
            double z,
            double maxDistance
    );
}
