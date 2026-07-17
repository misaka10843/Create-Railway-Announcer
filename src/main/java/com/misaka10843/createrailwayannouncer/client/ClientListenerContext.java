package com.misaka10843.createrailwayannouncer.client;

import com.misaka10843.createrailwayannouncer.CreateRailwayAnnouncer;
import com.misaka10843.createrailwayannouncer.compat.create.CreateTrainAdapter;
import com.misaka10843.createrailwayannouncer.compat.create.CreateTrainAdapterHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

public final class ClientListenerContext {

    private ClientListenerContext() {
    }

    public static ListenerPlace resolve(
            BlockPos stationPos,
            int horizontalRange,
            int verticalRange
    ) {
        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.player == null) {
            return ListenerPlace.OUTSIDE;
        }


        CreateTrainAdapter adapter =
                CreateTrainAdapterHolder.get();

        try {
            if (adapter.isAvailable()
                    && adapter.findTrainContainingClientPlayer(
                    minecraft.player
            ).isPresent()) {

                return ListenerPlace.ON_RELATED_TRAIN;
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