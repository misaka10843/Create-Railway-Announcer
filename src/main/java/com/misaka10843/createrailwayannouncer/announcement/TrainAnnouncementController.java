package com.misaka10843.createrailwayannouncer.announcement;

import com.misaka10843.createrailwayannouncer.CreateRailwayAnnouncer;
import com.misaka10843.createrailwayannouncer.compat.create.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TrainAnnouncementController {
    private static final Map<UUID, TrainAnnouncementState> STATES =
            new ConcurrentHashMap<>();

    private static final Map<UUID, PendingArrival> PENDING_ARRIVALS =
            new ConcurrentHashMap<>();

    private static final Map<UUID, PendingNextStop> PENDING_NEXT_STOPS =
            new ConcurrentHashMap<>();
    private static final Map<UUID, String> DEPARTED_STATIONS =
            new ConcurrentHashMap<>();
    private static long lastTick = 0;

    private TrainAnnouncementController() {
    }

    public static void tick(MinecraftServer server) {
        if (server == null) {
            return;
        }

        long gameTime = server.overworld().getGameTime();

        if (gameTime - lastTick < 20) {
            return;
        }

        lastTick = gameTime;

        CreateTrainAdapter adapter =
                CreateTrainAdapterHolder.get();

        if (!adapter.isAvailable()) {
            return;
        }

        if (!(adapter instanceof Create6TrainAdapter create)) {
            return;
        }

        Set<UUID> alive =
                new HashSet<>();

        create.allTrains()
                .forEach(train ->
                        alive.add(train.id)
                );

        TrainSnapshotCache.removeMissing(alive);

        STATES.keySet()
                .removeIf(id -> !alive.contains(id));

        PENDING_ARRIVALS.keySet()
                .removeIf(id -> !alive.contains(id));

        PENDING_NEXT_STOPS.keySet()
                .removeIf(id -> !alive.contains(id));
        DEPARTED_STATIONS.keySet()
                .removeIf(id -> !alive.contains(id));

        for (ServerLevel level : server.getAllLevels()) {
            scanLevel(
                    level,
                    create
            );
        }
    }

    private static void scanLevel(
            ServerLevel level,
            Create6TrainAdapter create
    ) {
        create.allTrains()
                .forEach(train -> {
                    CreateTrainSnapshot snapshot =
                            create.snapshot(train);

                    TrainSnapshotCache.update(
                            train.id,
                            snapshot,
                            level.getGameTime()
                    );

                    handleTrain(
                            level,
                            snapshot
                    );
                });
    }

    private static void handleTrain(
            ServerLevel level,
            CreateTrainSnapshot train
    ) {
        TrainAnnouncementState previous =
                STATES.get(
                        train.trainId()
                );

        if (previous == null) {
            STATES.put(
                    train.trainId(),
                    new TrainAnnouncementState(
                            train.trainId(),
                            train,
                            train.moving()
                    )
            );

            return;
        }

        if (!previous.moving()
                && train.moving()) {
            onTrainDeparted(
                    level,
                    previous.snapshot(),
                    train
            );
        }

        if (previous.moving()
                && !train.moving()) {
            onTrainStopped(
                    previous.snapshot(),
                    train
            );
        }

        dispatchPendingNextStop(
                level,
                train
        );

        dispatchPendingArrival(
                level,
                train
        );

        STATES.put(
                train.trainId(),
                new TrainAnnouncementState(
                        train.trainId(),
                        train,
                        train.moving()
                )
        );
    }

    private static void onTrainDeparted(
            ServerLevel level,
            CreateTrainSnapshot previous,
            CreateTrainSnapshot current
    ) {
        PENDING_ARRIVALS.remove(
                current.trainId()
        );

        String departed =
                stationName(
                        previous.currentStation()
                );

        if (!departed.isBlank()) {
            DEPARTED_STATIONS.put(
                    current.trainId(),
                    departed
            );
        }

        String next =
                stationName(
                        previous.nextStation()
                );

        if (!next.isBlank()) {
            PENDING_NEXT_STOPS.put(
                    current.trainId(),
                    new PendingNextStop(
                            previous,
                            level.getGameTime() + 20L
                    )
            );
        }

        CreateRailwayAnnouncer.LOGGER.info(
                "Train departed: {}, from={}, next={}",
                current.trainName(),
                departed,
                next
        );
    }

    private static void onTrainStopped(
            CreateTrainSnapshot previous,
            CreateTrainSnapshot current
    ) {
        PENDING_NEXT_STOPS.remove(
                current.trainId()
        );

        String departed =
                DEPARTED_STATIONS.getOrDefault(
                        current.trainId(),
                        ""
                );

        PENDING_ARRIVALS.put(
                current.trainId(),
                new PendingArrival(
                        departed
                )
        );
    }
    private static void dispatchPendingNextStop(
            ServerLevel level,
            CreateTrainSnapshot train
    ) {
        PendingNextStop pending =
                PENDING_NEXT_STOPS.get(
                        train.trainId()
                );

        if (pending == null) {
            return;
        }

        if (level.getGameTime() < pending.triggerGameTime()) {
            return;
        }

        if (!train.moving()) {
            return;
        }

        if (pending.snapshot().nextStation() == null) {
            PENDING_NEXT_STOPS.remove(
                    train.trainId()
            );

            return;
        }

        AnnouncementEvent event =
                TrainAnnouncementFactory.createNextStation(
                        pending.snapshot(),
                        pending.snapshot().position(),
                        level.getGameTime()
                );

        AnnouncementDispatcher.dispatchServerEvent(
                level,
                event
        );

        PENDING_NEXT_STOPS.remove(
                train.trainId()
        );
    }

    private static void dispatchPendingArrival(
            ServerLevel level,
            CreateTrainSnapshot train
    ) {
        PendingArrival pending =
                PENDING_ARRIVALS.get(
                        train.trainId()
                );

        if (pending == null) {
            return;
        }

        if (train.moving()) {
            return;
        }

        String current =
                stationName(
                        train.currentStation()
                );

        if (current.isBlank()) {
            return;
        }

        if (!pending.departedStation().isBlank()
                && current.equals(pending.departedStation())) {
            CreateRailwayAnnouncer.LOGGER.debug(
                    "Pending arrival waiting station update: train={}, departed={}, current={}",
                    train.trainName(),
                    pending.departedStation(),
                    current
            );

            return;
        }

        AnnouncementEvent event =
                TrainAnnouncementFactory.createArrived(
                        train,
                        train.position(),
                        level.getGameTime()
                );
        AnnouncementDispatcher.dispatchServerEvent(
                level,
                event
        );

        PENDING_ARRIVALS.remove(
                train.trainId()
        );
        DEPARTED_STATIONS.remove(
                train.trainId()
        );
    }

    private static String stationName(
            CreateStationSnapshot station
    ) {
        if (station == null || station.name() == null) {
            return "";
        }

        return station.name();
    }

    private record PendingArrival(
            String departedStation
    ) {
    }

    private record PendingNextStop(
            CreateTrainSnapshot snapshot,
            long triggerGameTime
    ) {
    }
}