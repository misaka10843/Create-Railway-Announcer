package com.misaka10843.create_railway_announcer.compat.create;

import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;
import java.util.UUID;

public final class MissingCreateTrainAdapter implements CreateTrainAdapter {

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public Optional<CreateTrainSnapshot> findTrain(UUID trainId) {
        return Optional.empty();
    }

    @Override
    public Optional<CreateTrainSnapshot> findTrainContainingClientPlayer(
            Player player
    ) {
        return Optional.empty();
    }

    @Override
    public Optional<CreateTrainSnapshot> findTrainContainingServerPlayer(
            ServerPlayer player
    ) {
        return Optional.empty();
    }

    @Override
    public Optional<CreateTrainSnapshot> findNearestTrain(
            ServerLevel level,
            double x,
            double y,
            double z,
            double maxDistance
    ) {
        return Optional.empty();
    }
}
