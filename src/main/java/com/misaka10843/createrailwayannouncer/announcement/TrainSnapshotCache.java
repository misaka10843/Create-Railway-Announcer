package com.misaka10843.createrailwayannouncer.announcement;

import com.misaka10843.createrailwayannouncer.compat.create.CreateTrainSnapshot;
import net.minecraft.server.level.ServerLevel;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TrainSnapshotCache {

    private static final Map<UUID, Entry> CACHE =
            new ConcurrentHashMap<>();

    private TrainSnapshotCache() {
    }


    public static CreateTrainSnapshot get(
            UUID trainId
    ) {
        Entry entry = CACHE.get(trainId);

        if (entry == null) {
            return null;
        }

        return entry.snapshot();
    }


    public static void update(
            UUID trainId,
            CreateTrainSnapshot snapshot,
            long tick
    ) {
        CACHE.put(
                trainId,
                new Entry(
                        snapshot,
                        tick
                )
        );
    }


    public static void removeMissing(
            java.util.Set<UUID> alive
    ) {
        CACHE.keySet()
                .removeIf(id -> !alive.contains(id));
    }


    public static int size() {
        return CACHE.size();
    }


    private record Entry(
            CreateTrainSnapshot snapshot,
            long tick
    ) {
    }
}