package com.misaka10843.createrailwayannouncer.announcement;

import com.misaka10843.createrailwayannouncer.CreateRailwayAnnouncer;
import com.misaka10843.createrailwayannouncer.announcement.source.AnnouncementSource;
import com.misaka10843.createrailwayannouncer.announcement.source.AnnouncementSourceType;
import com.misaka10843.createrailwayannouncer.announcement.source.ServerAnnouncementSourceRegistry;
import com.misaka10843.createrailwayannouncer.compat.create.CreateTrainAdapterHolder;
import com.misaka10843.createrailwayannouncer.config.ServerConfig;
import com.misaka10843.createrailwayannouncer.config.StationConfig;
import com.misaka10843.createrailwayannouncer.config.StationLineConfigStore;
import com.misaka10843.createrailwayannouncer.network.AnnouncementPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import java.util.UUID;

public final class ServerAnnouncementDispatcher {
    private ServerAnnouncementDispatcher() {
    }

    private static BlockPos resolveCenter(
            ServerAnnouncementRequest request,
            StationConfig stationConfig
    ) {
        if (stationConfig != null && stationConfig.hasPosition()) {
            return stationConfig.getPosition();
        }

        if (request.stationPos() != null
                && !request.stationPos().equals(BlockPos.ZERO)) {
            return request.stationPos();
        }

        return null;
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

        StationConfig stationConfig = stationConfig(request);

        if (stationConfig != null
                && stationConfig.hasPosition()
                && !stationConfig.getDimension().equals(level.dimension().location().toString())) {
            return DispatchResult.rejected(
                    "station dimension mismatch: station="
                            + stationConfig.getDimension()
                            + ", level="
                            + level.dimension().location()
            );
        }

        List<AnnouncementSource> sources =
                ServerAnnouncementSourceRegistry.findSources(
                        level,
                        request
                );

        if (!sources.isEmpty()) {
            return dispatchToAnnouncementSources(
                    level,
                    request,
                    sources
            );
        }

        BlockPos center = resolveCenter(request, stationConfig);
        if (center == null) {
            return DispatchResult.rejected("station position is null and no announcement source found");
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

        UUID announcementId = UUID.randomUUID();
        long startGameTime = level.getGameTime();

        AnnouncementPacket packet = new AnnouncementPacket(
                announcementId,
                request.eventType(),
                AnnouncementSequenceMapper.sequenceId(
                        request.eventType()
                ),
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
                startGameTime,
                false
        );


        int sent = 0;
        Set<UUID> sentPlayerIds = new HashSet<>();

        for (ServerPlayer player : level.players()) {
            if (!isPlayerInRange(player, center, horizontalRange, verticalRange)) {
                continue;
            }

            PacketDistributor.sendToPlayer(player, packet);
            sentPlayerIds.add(player.getUUID());
            sent++;
        }

        ServerActiveAnnouncementRegistry.register(
                level,
                request,
                announcementId,
                center,
                horizontalRange,
                verticalRange,
                startGameTime,
                sentPlayerIds
        );

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

    private static DispatchResult dispatchToAnnouncementSources(
            ServerLevel level,
            ServerAnnouncementRequest request,
            List<AnnouncementSource> sources
    ) {
        ServerAnnouncementCooldown.CooldownResult cooldownResult =
                ServerAnnouncementCooldown.tryAcquire(
                        level,
                        request,
                        level.getGameTime()
                );

        if (!cooldownResult.allowed()) {
            CreateRailwayAnnouncer.LOGGER.info(
                    "Suppressed duplicate source announcement: key={}, remainingTicks={}",
                    cooldownResult.key(),
                    cooldownResult.remainingTicks()
            );

            return DispatchResult.suppressed(
                    0,
                    0,
                    cooldownResult.remainingTicks()
            );
        }

        UUID announcementId = UUID.randomUUID();
        long startGameTime = level.getGameTime();

        int sent = 0;
        Set<UUID> sentPlayerIds = new HashSet<>();

        for (AnnouncementSource source : sources) {
            AnnouncementPacket packet = new AnnouncementPacket(
                    announcementId,
                    request.eventType(),
                    AnnouncementSequenceMapper.sequenceId(
                            request.eventType()
                    ),
                    nonNullUuid(request.trainId()),
                    nullToEmpty(request.trainName()),
                    nonNullUuid(request.stationId()),
                    source.position(),
                    nullToEmpty(request.currentStationConfigId()),
                    nullToEmpty(request.nextStationConfigId()),
                    nullToEmpty(request.destinationStationConfigId()),
                    source.horizontalRange(),
                    source.verticalRange(),
                    request.channel(),
                    request.priority(),
                    startGameTime,
                    startGameTime,
                    false
            );

            Set<UUID> sourceSentPlayerIds = new HashSet<>();

            for (ServerPlayer player : level.players()) {
                if (sentPlayerIds.contains(player.getUUID())) {
                    continue;
                }

                if (source.type() == AnnouncementSourceType.ONBOARD) {

                    if (!playerInTrain(
                            player,
                            source.trainId()
                    )) {
                        continue;
                    }

                } else {

                    if (!isPlayerInRange(
                            player,
                            source.position(),
                            source.horizontalRange(),
                            source.verticalRange()
                    )) {
                        continue;
                    }
                }

                PacketDistributor.sendToPlayer(player, packet);
                sentPlayerIds.add(player.getUUID());
                sourceSentPlayerIds.add(player.getUUID());
                sent++;
            }

            ServerActiveAnnouncementRegistry.register(
                    level,
                    request,
                    announcementId,
                    source.position(),
                    source.horizontalRange(),
                    source.verticalRange(),
                    startGameTime,
                    sourceSentPlayerIds
            );

            CreateRailwayAnnouncer.LOGGER.info(
                    "Dispatched announcement from source: event={}, source={}, type={}, pos={}, range={}x{}, players={}",
                    request.eventType(),
                    source.sourceId(),
                    source.type(),
                    source.position(),
                    source.horizontalRange(),
                    source.verticalRange(),
                    sourceSentPlayerIds.size()
            );
        }

        return DispatchResult.sent(
                sent,
                sources.get(0).horizontalRange(),
                sources.get(0).verticalRange()
        );
    }

    private static boolean playerInTrain(
            ServerPlayer player,
            UUID trainId
    ) {
        if (trainId == null) {
            return false;
        }

        return CreateTrainAdapterHolder.get()
                .findTrainContainingServerPlayer(player)
                .map(train ->
                        train.trainId().equals(trainId)
                )
                .orElse(false);
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

    public static DispatchResult dispatch(ServerLevel level, AnnouncementEvent event) {
        if (level == null || event == null) {
            return DispatchResult.rejected("level or event is null");
        }

        String sequenceId = AnnouncementSequenceMapper.sequenceId(event.type());
        if (sequenceId == null) {
            return DispatchResult.rejected("no sequence for event: " + event.type());
        }

        CreateRailwayAnnouncer.LOGGER.info(
                "Resolved announcement sequence: event={}, sequence={}",
                event.type(),
                sequenceId
        );

        ServerAnnouncementRequest request = ServerAnnouncementRequest.of(
                event.type(),
                event.trainId(),
                event.trainName(),
                event.stationId(),
                event.stationPos(),
                event.currentStationConfigId(),
                event.nextStationConfigId(),
                event.destinationStationConfigId(),
                event.channel(),
                event.priority()
        );

        DispatchResult result = dispatchToNearbyPlayers(level, request);

        CreateRailwayAnnouncer.LOGGER.info(
                "Train announcement dispatch result: event={}, accepted={}, suppressed={}, players={}, message={}",
                event.type(),
                result.accepted(),
                result.suppressed(),
                result.sentPlayers(),
                result.message()
        );

        return result;
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