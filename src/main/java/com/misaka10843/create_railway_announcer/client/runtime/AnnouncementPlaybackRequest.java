package com.misaka10843.create_railway_announcer.client.runtime;

import com.misaka10843.create_railway_announcer.announcement.AnnouncementEventType;
import com.misaka10843.create_railway_announcer.network.AnnouncementPacket;

import java.util.UUID;

public record AnnouncementPlaybackRequest(
        AnnouncementEventType eventType,
        String sequenceId,
        String stationId,
        String trainId,
        net.minecraft.core.BlockPos position,
        int horizontalRange,
        int verticalRange
) {
    public AnnouncementPlaybackRequest(AnnouncementEventType event, String sequence, String station, String train) { this(event,sequence,station,train,null,0,0); }
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
                packet.sequenceId(),
                stationIdFor(packet),
                uuidToString(packet.trainId()), packet.stationPos(), packet.horizontalRange(), packet.verticalRange()
        );
    }

    private static String stationIdFor(AnnouncementPacket packet) {
        boolean next = packet.eventType() == AnnouncementEventType.ONBOARD_NEXT_STOP || packet.eventType() == AnnouncementEventType.ONBOARD_APPROACHING
                || packet.eventType() == AnnouncementEventType.PLATFORM_APPROACH;
        if (!next && notBlank(packet.currentStationConfigId())) return packet.currentStationConfigId();
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
