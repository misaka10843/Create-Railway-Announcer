package com.misaka10843.create_railway_announcer.announcement;

import com.misaka10843.create_railway_announcer.CreateRailwayAnnouncer;
import com.misaka10843.create_railway_announcer.compat.create.*;
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
    private static final Map<UUID, UUID> APPROACHES = new ConcurrentHashMap<>();

    private TrainAnnouncementController() {
    }

    public static void clear() {
        STATES.clear();
        APPROACHES.clear();
        PENDING_ARRIVALS.clear();
        PENDING_NEXT_STOPS.clear();
        DEPARTED_STATIONS.clear();
        TrainSnapshotCache.removeMissing(java.util.Set.of());
        lastTick = 0;
    }

    public static void tick(MinecraftServer server) {
        if (server == null) {
            return;
        }

        long gameTime = server.overworld().getGameTime();

        if (gameTime - lastTick < com.misaka10843.create_railway_announcer.config.ServerConfig.APPROACH_CHECK_INTERVAL_TICKS.get()) {
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
        APPROACHES.keySet().removeIf(id -> !alive.contains(id));

        PENDING_ARRIVALS.keySet()
                .removeIf(id -> !alive.contains(id));

        PENDING_NEXT_STOPS.keySet()
                .removeIf(id -> !alive.contains(id));
        DEPARTED_STATIONS.keySet()
                .removeIf(id -> !alive.contains(id));

        for (var train : create.allTrains()) {
            for (ServerLevel level : server.getAllLevels()) {
                if (create.position(train, level).isEmpty()) continue;
                CreateTrainSnapshot snapshot = create.snapshot(train).at(create.position(train, level).orElseThrow());
                TrainSnapshotCache.update(train.id, snapshot, gameTime);
                handleTrain(level, snapshot);
                break;
            }
        }
    }

    private static void handleTrain(
            ServerLevel level,
            CreateTrainSnapshot train
    ) {
        if (train.moving() && train.nextStation() != null && Create6TrainAdapter.approaching(train.trainId())
                && !train.nextStation().stationId().equals(APPROACHES.get(train.trainId()))) {
            platformEvent(level,train,train.nextStation(),AnnouncementEventType.PLATFORM_APPROACH);
            onboardEvent(level,train,train.nextStation(),AnnouncementEventType.ONBOARD_APPROACHING);
            APPROACHES.put(train.trainId(),train.nextStation().stationId());
        }
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
        if (previous.currentStation() == null) return;
        platformEvent(level,current,previous.currentStation(),AnnouncementEventType.PLATFORM_DEPARTED);
        onboardEvent(level,current,previous.currentStation(),AnnouncementEventType.ONBOARD_DEPARTING);
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
                            level.getGameTime() + 60L
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

        onboardEvent(level,train,train.currentStation(),Create6TrainAdapter.terminal(train.trainId())?AnnouncementEventType.ONBOARD_TERMINAL:AnnouncementEventType.ONBOARD_ARRIVED);

        PENDING_ARRIVALS.remove(
                train.trainId()
        );
        platformEvent(level,train,train.currentStation(),AnnouncementEventType.PLATFORM_ARRIVAL);
        APPROACHES.remove(train.trainId());
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

    private static void platformEvent(ServerLevel level, CreateTrainSnapshot train, CreateStationSnapshot station, AnnouncementEventType type) {
        if (station == null) return;
        var request = ServerAnnouncementRequest.of(type, train.trainId(), train.trainName(), station.stationId(), station.position(),
                station.name(), station.name(), station.name(), com.misaka10843.create_railway_announcer.audio.AudioChannel.PLATFORM_VOICE,80);
        ServerAnnouncementDispatcher.dispatchToNearbyPlayers(level,request);
    }
    public static void onboardEvent(ServerLevel level,CreateTrainSnapshot train,CreateStationSnapshot station,AnnouncementEventType type){
        if(station==null)return;
        var request=ServerAnnouncementRequest.of(type,train.trainId(),train.trainName(),station.stationId(),station.position(),station.name(),station.name(),station.name(),com.misaka10843.create_railway_announcer.audio.AudioChannel.ONBOARD_VOICE,80);
        ServerAnnouncementDispatcher.dispatchToNearbyPlayers(level,request);
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
