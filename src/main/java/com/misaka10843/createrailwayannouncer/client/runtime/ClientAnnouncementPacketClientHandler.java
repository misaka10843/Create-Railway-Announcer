package com.misaka10843.createrailwayannouncer.client.runtime;

import com.misaka10843.createrailwayannouncer.CreateRailwayAnnouncer;
import com.misaka10843.createrailwayannouncer.network.AnnouncementPacket;
import com.misaka10843.createrailwayannouncer.playback.PlaybackSession;

public final class ClientAnnouncementPacketClientHandler {
    private ClientAnnouncementPacketClientHandler() {
    }

    public static void handle(AnnouncementPacket packet) {
        AnnouncementPlaybackRequest request = AnnouncementPlaybackRequest.fromPacket(packet);

        ClientAnnouncementServices.runtime().play(request).whenComplete((result, throwable) -> {
            if (throwable != null) {
                CreateRailwayAnnouncer.LOGGER.error(
                        "Failed to play announcement packet: id={}, event={}",
                        packet.announcementId(),
                        packet.eventType(),
                        throwable
                );
                return;
            }

            if (result == null) {
                CreateRailwayAnnouncer.LOGGER.warn(
                        "Announcement packet produced empty playback result: id={}, event={}",
                        packet.announcementId(),
                        packet.eventType()
                );
                return;
            }

            if (!result.accepted()) {
                CreateRailwayAnnouncer.LOGGER.info(
                        "Announcement packet rejected by playback manager: id={}, event={}, reason={}",
                        packet.announcementId(),
                        packet.eventType(),
                        result.message()
                );
                return;
            }

            PlaybackSession session = result.session();

            CreateRailwayAnnouncer.LOGGER.info(
                    "Announcement packet playback started: packetId={}, event={}, sequence={}, channel={}, priority={}, decision={}",
                    packet.announcementId(),
                    packet.eventType(),
                    session.sequence().id(),
                    session.channel(),
                    session.sequence().priority(),
                    result.decision()
            );
        });
    }
}