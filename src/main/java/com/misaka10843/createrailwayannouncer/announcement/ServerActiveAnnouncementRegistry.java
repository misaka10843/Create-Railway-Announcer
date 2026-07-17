package com.misaka10843.createrailwayannouncer.announcement;

import com.misaka10843.createrailwayannouncer.CreateRailwayAnnouncer;
import com.misaka10843.createrailwayannouncer.network.AnnouncementPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class ServerActiveAnnouncementRegistry {
    private static final List<ServerActiveAnnouncementSession> ACTIVE_SESSIONS = new ArrayList<>();
    private static long lastScanGameTime = -1L;

    private ServerActiveAnnouncementRegistry() {
    }

    public static synchronized void register(
            ServerLevel level,
            ServerAnnouncementRequest request,
            UUID announcementId,
            BlockPos center,
            int horizontalRange,
            int verticalRange,
            long startGameTime,
            Set<UUID> initiallySentPlayerIds
    ) {
        long durationTicks = estimatedDurationTicks(request.eventType());
        long expireGameTime = startGameTime + durationTicks;

        ServerActiveAnnouncementSession session = new ServerActiveAnnouncementSession(
                announcementId,
                level.dimension().location(),
                request.eventType(),
                nonNullUuid(request.trainId()),
                nullToEmpty(request.trainName()),
                nonNullUuid(request.stationId()),
                center,
                nullToEmpty(request.currentStationConfigId()),
                nullToEmpty(request.nextStationConfigId()),
                nullToEmpty(request.destinationStationConfigId()),
                horizontalRange,
                verticalRange,
                request.channel(),
                request.priority(),
                startGameTime,
                expireGameTime,
                initiallySentPlayerIds
        );

        ACTIVE_SESSIONS.add(session);

        CreateRailwayAnnouncer.LOGGER.info(
                "Registered active announcement session: id={}, event={}, station={}, center={}, range={}x{}, durationTicks={}, initialPlayers={}",
                announcementId,
                request.eventType(),
                currentEventStationConfigId(request),
                center,
                horizontalRange,
                verticalRange,
                durationTicks,
                initiallySentPlayerIds == null ? 0 : initiallySentPlayerIds.size()
        );
    }

    public static synchronized void serverTick(MinecraftServer server) {
        if (server == null || ACTIVE_SESSIONS.isEmpty()) {
            return;
        }

        long gameTime = firstLevelGameTime(server);
        if (gameTime < 0) {
            return;
        }

        if (lastScanGameTime >= 0 && gameTime - lastScanGameTime < 10) {
            return;
        }

        lastScanGameTime = gameTime;

        Iterator<ServerActiveAnnouncementSession> iterator = ACTIVE_SESSIONS.iterator();
        while (iterator.hasNext()) {
            ServerActiveAnnouncementSession session = iterator.next();

            if (session.isExpired(gameTime)) {
                iterator.remove();

                CreateRailwayAnnouncer.LOGGER.info(
                        "Expired active announcement session: id={}, event={}, sentPlayers={}",
                        session.announcementId(),
                        session.eventType(),
                        session.sentPlayerCount()
                );
                continue;
            }

            ServerLevel level = findLevel(server, session);
            if (level == null) {
                iterator.remove();
                continue;
            }

            scanLatePlayers(level, session, gameTime);
        }
    }

    public static synchronized int clear() {
        int size = ACTIVE_SESSIONS.size();
        ACTIVE_SESSIONS.clear();
        return size;
    }

    public static synchronized int size() {
        return ACTIVE_SESSIONS.size();
    }

    private static void scanLatePlayers(
            ServerLevel level,
            ServerActiveAnnouncementSession session,
            long gameTime
    ) {
        for (ServerPlayer player : level.players()) {
            UUID playerId = player.getUUID();

            if (session.hasSentTo(playerId)) {
                continue;
            }

            if (!isPlayerInRange(
                    player,
                    session.stationPos(),
                    session.horizontalRange(),
                    session.verticalRange()
            )) {
                continue;
            }

            AnnouncementPacket packet = new AnnouncementPacket(
                    session.announcementId(),
                    session.eventType(),
                    AnnouncementSequenceMapper.sequenceId(
                            session.eventType()
                    ),
                    session.trainId(),
                    session.trainName(),
                    session.stationId(),
                    session.stationPos(),
                    session.currentStationConfigId(),
                    session.nextStationConfigId(),
                    session.destinationStationConfigId(),
                    session.horizontalRange(),
                    session.verticalRange(),
                    session.channel(),
                    session.priority(),
                    session.startGameTime(),
                    gameTime,
                    true
            );

            PacketDistributor.sendToPlayer(player, packet);
            session.markSent(playerId);

            CreateRailwayAnnouncer.LOGGER.info(
                    "Sent catch-up announcement packet: id={}, event={}, player={}, elapsedTicks={}",
                    session.announcementId(),
                    session.eventType(),
                    player.getGameProfile().getName(),
                    gameTime - session.startGameTime()
            );
        }
    }

    private static ServerLevel findLevel(
            MinecraftServer server,
            ServerActiveAnnouncementSession session
    ) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension().location().equals(session.levelId())) {
                return level;
            }
        }

        return null;
    }

    private static long firstLevelGameTime(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            return level.getGameTime();
        }

        return -1L;
    }

    private static boolean isPlayerInRange(
            ServerPlayer player,
            BlockPos center,
            int horizontalRange,
            int verticalRange
    ) {
        BlockPos playerPos = player.blockPosition();

        int dx = Math.abs(playerPos.getX() - center.getX());
        int dz = Math.abs(playerPos.getZ() - center.getZ());
        int dy = Math.abs(playerPos.getY() - center.getY());

        return dx <= horizontalRange
                && dz <= horizontalRange
                && dy <= verticalRange;
    }

    private static long estimatedDurationTicks(AnnouncementEventType eventType) {
        return switch (eventType) {
            case ONBOARD_NEXT_STOP -> 360L;
            case ONBOARD_APPROACHING -> 260L;
            case ONBOARD_ARRIVED -> 180L;
            case ONBOARD_DEPARTING -> 180L;
            case ONBOARD_TERMINAL -> 260L;

            case PLATFORM_APPROACH -> 240L;
            case PLATFORM_ARRIVAL -> 200L;
            case PLATFORM_PRE_DEPARTURE -> 200L;
            case PLATFORM_DEPARTURE_MELODY -> 600L;
            case PLATFORM_DOOR_CLOSING -> 180L;
            case PLATFORM_DEPARTED -> 160L;

            case DOOR_OPENING -> 100L;
            case DOOR_CLOSING -> 180L;
            case SAFETY_NOTICE -> 240L;
        };
    }

    private static String currentEventStationConfigId(ServerAnnouncementRequest request) {
        return switch (request.eventType()) {
            case ONBOARD_ARRIVED,
                 PLATFORM_ARRIVAL,
                 DOOR_OPENING -> firstNonBlank(
                    request.currentStationConfigId(),
                    request.nextStationConfigId(),
                    request.destinationStationConfigId()
            );

            case ONBOARD_NEXT_STOP,
                 ONBOARD_APPROACHING,
                 PLATFORM_APPROACH -> firstNonBlank(
                    request.nextStationConfigId(),
                    request.currentStationConfigId(),
                    request.destinationStationConfigId()
            );

            default -> firstNonBlank(
                    request.currentStationConfigId(),
                    request.nextStationConfigId(),
                    request.destinationStationConfigId()
            );
        };
    }
    private static String firstNonBlank(
            String first,
            String second,
            String third
    ) {
        if (notBlank(first)) {
            return first;
        }

        if (notBlank(second)) {
            return second;
        }

        if (notBlank(third)) {
            return third;
        }

        return "";
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static UUID nonNullUuid(UUID uuid) {
        return uuid == null ? new UUID(0L, 0L) : uuid;
    }
}