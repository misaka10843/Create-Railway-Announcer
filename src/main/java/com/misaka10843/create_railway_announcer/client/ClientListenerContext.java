package com.misaka10843.create_railway_announcer.client;

import com.misaka10843.create_railway_announcer.CreateRailwayAnnouncer;
import com.misaka10843.create_railway_announcer.compat.create.CreateTrainAdapter;
import com.misaka10843.create_railway_announcer.compat.create.CreateTrainAdapterHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

public final class ClientListenerContext {

    private ClientListenerContext() {
    }

    public static ListenerPlace resolve(
            BlockPos stationPos,
            int horizontalRange,
            int verticalRange,
            java.util.UUID relatedTrain
    ) {
        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.player == null) {
            return ListenerPlace.OUTSIDE;
        }


        CreateTrainAdapter adapter =
                CreateTrainAdapterHolder.get();

        try {
            if (adapter.isAvailable()) {
                var train = adapter.ridingTrainId(minecraft.player);
                if (train.isPresent()) return train.get().equals(relatedTrain)
                        ? ListenerPlace.ON_RELATED_TRAIN : ListenerPlace.ON_OTHER_TRAIN;
            }

        } catch (Throwable t) {
            CreateRailwayAnnouncer.LOGGER.warn(
                    "Failed checking player train state",
                    t
            );
        }

        BlockPos playerPos = minecraft.player.blockPosition();

        if (stationPos != null
                && isInsideRange(
                playerPos,
                stationPos,
                horizontalRange,
                verticalRange
        )) {

            return ListenerPlace.IN_STATION_AREA;
        }


        return ListenerPlace.OUTSIDE;
    }

    private static boolean isInsideRange(
            BlockPos player,
            BlockPos center,
            int horizontalRange,
            int verticalRange
    ) {
        int dx = Math.abs(player.getX() - center.getX());
        int dz = Math.abs(player.getZ() - center.getZ());
        int dy = Math.abs(player.getY() - center.getY());

        return dx <= horizontalRange
                && dz <= horizontalRange
                && dy <= verticalRange;
    }
}
