package com.misaka10843.createrailwayannouncer.client.runtime;

import com.misaka10843.createrailwayannouncer.announcement.AnnouncementEventType;
import com.misaka10843.createrailwayannouncer.network.AnnouncementPacket;

import java.util.UUID;

public record AnnouncementPlaybackRequest(
        AnnouncementEventType eventType,
        String sequenceId,
        String stationId,
        String trainId
) {
    public static AnnouncementPlaybackRequest sequenceOnly(String sequenceId) {
        return new AnnouncementPlaybackRequest(
                AnnouncementEventType.ONBOARD_NEXT_STOP,
                sequenceId,
                "",
                ""
        );
    }

    public static AnnouncementPlaybackRequest fromPacket(AnnouncementPacket packet) {
        return new AnnouncementPlaybackRequest(
                packet.eventType(),
                sequenceIdFor(packet.eventType()),
                stationIdFor(packet),
                uuidToString(packet.trainId())
        );
    }

    private static String sequenceIdFor(AnnouncementEventType eventType) {
        return switch (eventType) {
            case PLATFORM_DOOR_CLOSING, DOOR_CLOSING, SAFETY_NOTICE -> "priority_high_test";
            case ONBOARD_NEXT_STOP -> "onboard_next_stop_test";
            default -> "onboard_next_stop_test";
        };
    }

    private static String stationIdFor(AnnouncementPacket packet) {
        if (notBlank(packet.nextStationConfigId())) {
            return packet.nextStationConfigId();
        }

        if (notBlank(packet.currentStationConfigId())) {
            return packet.currentStationConfigId();
        }

        if (notBlank(packet.destinationStationConfigId())) {
            return packet.destinationStationConfigId();
        }

        return uuidToString(packet.stationId());
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static String uuidToString(UUID uuid) {
        return uuid == null ? "" : uuid.toString();
    }
}