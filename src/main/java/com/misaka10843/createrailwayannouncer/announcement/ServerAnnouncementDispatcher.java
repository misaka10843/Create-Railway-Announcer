package com.misaka10843.createrailwayannouncer.announcement;

import com.misaka10843.createrailwayannouncer.CreateRailwayAnnouncer;
import com.misaka10843.createrailwayannouncer.config.ServerConfig;
import com.misaka10843.createrailwayannouncer.config.StationConfig;
import com.misaka10843.createrailwayannouncer.config.StationLineConfigStore;
import com.misaka10843.createrailwayannouncer.network.AnnouncementPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.UUID;

public final class ServerAnnouncementDispatcher {
    private ServerAnnouncementDispatcher() {
    }

    public static DispatchResult dispatchToNearbyPlayers(
            ServerLevel level,
            ServerAnnouncementRequest request
    ) {
        if (level == null || request == null) {
            return DispatchResult.rejected("level or request is null");
        }

        if (!isEventEnabled(request.eventType())) {
            return DispatchResult.rejected("event disabled by server config: " + request.eventType());
        }

        BlockPos center = request.stationPos();
        if (center == null) {
            return DispatchResult.rejected("station position is null");
        }

        int horizontalRange = resolveHorizontalRange(request);
        int verticalRange = resolveVerticalRange(request);

        ServerAnnouncementCooldown.CooldownResult cooldownResult =
                ServerAnnouncementCooldown.tryAcquire(level, request, level.getGameTime());

        if (!cooldownResult.allowed()) {
            CreateRailwayAnnouncer.LOGGER.info(
                    "Suppressed duplicate announcement: key={}, remainingTicks={}",
                    cooldownResult.key(),
                    cooldownResult.remainingTicks()
            );

            return DispatchResult.suppressed(
                    horizontalRange,
                    verticalRange,
                    cooldownResult.remainingTicks()
            );
        }

        AnnouncementPacket packet = new AnnouncementPacket(
                UUID.randomUUID(),
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
                level.getGameTime()
        );

        int sent = 0;

        for (ServerPlayer player : level.players()) {
            if (!isPlayerInRange(player, center, horizontalRange, verticalRange)) {
                continue;
            }

            PacketDistributor.sendToPlayer(player, packet);
            sent++;
        }

        CreateRailwayAnnouncer.LOGGER.info(
                "Dispatched announcement: event={}, station={}, channel={}, priority={}, center={}, range={}x{}, players={}",
                request.eventType(),
                primaryStationConfigId(request),
                request.channel(),
                request.priority(),
                center,
                horizontalRange,
                verticalRange,
                sent
        );

        return DispatchResult.sent(sent, horizontalRange, verticalRange);
    }

    private static boolean isEventEnabled(AnnouncementEventType eventType) {
        return switch (eventType) {
            case ONBOARD_NEXT_STOP,
                 ONBOARD_APPROACHING,
                 ONBOARD_ARRIVED,
                 ONBOARD_DEPARTING,
                 ONBOARD_TERMINAL,
                 DOOR_OPENING,
                 DOOR_CLOSING,
                 SAFETY_NOTICE -> ServerConfig.ENABLE_ONBOARD_ANNOUNCEMENTS.get();

            case PLATFORM_APPROACH,
                 PLATFORM_ARRIVAL,
                 PLATFORM_PRE_DEPARTURE,
                 PLATFORM_DEPARTURE_MELODY,
                 PLATFORM_DOOR_CLOSING,
                 PLATFORM_DEPARTED -> ServerConfig.ENABLE_PLATFORM_ANNOUNCEMENTS.get();
        };
    }

    private static int resolveHorizontalRange(ServerAnnouncementRequest request) {
        if (request.horizontalRange() > 0) {
            return request.horizontalRange();
        }

        StationConfig station = stationConfig(request);
        if (station != null && station.getHorizontalRange() > 0) {
            return station.getHorizontalRange();
        }

        return ServerConfig.DEFAULT_STATION_HORIZONTAL_RANGE.get();
    }

    private static int resolveVerticalRange(ServerAnnouncementRequest request) {
        if (request.verticalRange() > 0) {
            return request.verticalRange();
        }

        StationConfig station = stationConfig(request);
        if (station != null && station.getVerticalRange() > 0) {
            return station.getVerticalRange();
        }

        return ServerConfig.DEFAULT_STATION_VERTICAL_RANGE.get();
    }

    private static StationConfig stationConfig(ServerAnnouncementRequest request) {
        String id = primaryStationConfigId(request);
        if (id.isBlank()) {
            return null;
        }

        return StationLineConfigStore.station(id).orElse(null);
    }

    private static String primaryStationConfigId(ServerAnnouncementRequest request) {
        if (notBlank(request.nextStationConfigId())) {
            return request.nextStationConfigId();
        }

        if (notBlank(request.currentStationConfigId())) {
            return request.currentStationConfigId();
        }

        if (notBlank(request.destinationStationConfigId())) {
            return request.destinationStationConfigId();
        }

        return "";
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

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static UUID nonNullUuid(UUID uuid) {
        return uuid == null ? UUID.randomUUID() : uuid;
    }

    public record DispatchResult(
            boolean accepted,
            boolean suppressed,
            int sentPlayers,
            int horizontalRange,
            int verticalRange,
            long remainingCooldownTicks,
            String message
    ) {
        public static DispatchResult sent(int sentPlayers, int horizontalRange, int verticalRange) {
            return new DispatchResult(
                    true,
                    false,
                    sentPlayers,
                    horizontalRange,
                    verticalRange,
                    0L,
                    "sent"
            );
        }

        public static DispatchResult suppressed(
                int horizontalRange,
                int verticalRange,
                long remainingCooldownTicks
        ) {
            return new DispatchResult(
                    false,
                    true,
                    0,
                    horizontalRange,
                    verticalRange,
                    remainingCooldownTicks,
                    "suppressed by cooldown"
            );
        }

        public static DispatchResult rejected(String message) {
            return new DispatchResult(
                    false,
                    false,
                    0,
                    0,
                    0,
                    0L,
                    message
            );
        }
    }
}