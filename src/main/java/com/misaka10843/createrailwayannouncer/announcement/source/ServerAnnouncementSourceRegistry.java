package com.misaka10843.createrailwayannouncer.announcement.source;

import com.misaka10843.createrailwayannouncer.CreateRailwayAnnouncer;
import com.misaka10843.createrailwayannouncer.announcement.AnnouncementEventType;
import com.misaka10843.createrailwayannouncer.announcement.ServerAnnouncementRequest;
import com.misaka10843.createrailwayannouncer.audio.AudioChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

public final class ServerAnnouncementSourceRegistry {
    private static final List<AnnouncementSource> SOURCES =
            new CopyOnWriteArrayList<>();

    private ServerAnnouncementSourceRegistry() {
    }

    public static AnnouncementSource addStationSource(
            ServerLevel level,
            String stationConfigId,
            BlockPos position,
            int horizontalRange,
            int verticalRange,
            AudioChannel channel
    ) {
        AnnouncementSource source = new AnnouncementSource(
                UUID.randomUUID(),
                level.dimension().location().toString(),
                AnnouncementSourceType.STATION,
                null,
                stationConfigId,
                "",
                position,
                horizontalRange,
                verticalRange,
                channel
        );

        SOURCES.add(source);
        return source;
    }

    public static AnnouncementSource addOnboardSource(
            ServerLevel level,
            UUID trainId,
            BlockPos position,
            int horizontalRange,
            int verticalRange
    ) {
        AnnouncementSource source = new AnnouncementSource(
                UUID.randomUUID(),
                level.dimension().location().toString(),
                AnnouncementSourceType.ONBOARD,
                trainId,
                "",
                "",
                position,
                horizontalRange,
                verticalRange,
                AudioChannel.ONBOARD_VOICE
        );

        SOURCES.add(source);
        return source;
    }

    public static List<AnnouncementSource> findSources(
            ServerLevel level,
            ServerAnnouncementRequest request
    ) {
        List<AnnouncementSource> result = new ArrayList<>();

        String levelId = level.dimension().location().toString();
        String stationId = primaryStationConfigId(request);

        for (AnnouncementSource source : SOURCES) {
            CreateRailwayAnnouncer.LOGGER.trace(
                    "Checking source: type={}, sourceTrain={}, requestTrain={}, channel={}, requestChannel={}",
                    source.type(),
                    source.trainId(),
                    request.trainId(),
                    source.channel(),
                    request.channel()
            );
            if (!source.levelId().equals(levelId)) {
                continue;
            }

            if (source.channel() != request.channel()) {
                continue;
            }

            if (source.type() == AnnouncementSourceType.ONBOARD) {
                if (source.trainId() != null
                        && source.trainId().equals(request.trainId())) {
                    result.add(source);
                }

                continue;
            }

            if (source.type() == AnnouncementSourceType.STATION
                    || source.type() == AnnouncementSourceType.PLATFORM) {
                if (!stationId.isBlank()
                        && source.stationConfigId().equals(stationId)) {
                    result.add(source);
                }
            }
        }

        return result;
    }

    public static List<AnnouncementSource> sources() {
        return List.copyOf(SOURCES);
    }

    public static int clear() {
        int size = SOURCES.size();
        SOURCES.clear();
        return size;
    }

    public static int size() {
        return SOURCES.size();
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

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}