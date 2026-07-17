package com.misaka10843.createrailwayannouncer.client.runtime;

import com.misaka10843.createrailwayannouncer.CreateRailwayAnnouncer;
import com.misaka10843.createrailwayannouncer.client.ClientAnnouncementFilter;
import com.misaka10843.createrailwayannouncer.client.ClientListenerContext;
import com.misaka10843.createrailwayannouncer.client.ListenerPlace;
import com.misaka10843.createrailwayannouncer.network.AnnouncementPacket;
import com.misaka10843.createrailwayannouncer.playback.PlaybackSession;

public final class ClientAnnouncementPacketClientHandler {
    private ClientAnnouncementPacketClientHandler() {
    }

    public static void handle(AnnouncementPacket packet) {
        AnnouncementPlaybackRequest request = AnnouncementPlaybackRequest.fromPacket(packet);

        ListenerPlace place =
                ClientListenerContext.resolve(
                        packet.stationPos(),
                        packet.horizontalRange(),
                        packet.verticalRange()
                );


        if (!ClientAnnouncementFilter.shouldPlay(packet, place)) {

            CreateRailwayAnnouncer.LOGGER.info(
                    "Ignored announcement packet: id={}, channel={}, listenerPlace={}",
                    packet.announcementId(),
                    packet.channel(),
                    place
            );

            return;
        }

        long offsetMs = offsetMsFor(packet);

        ClientAnnouncementServices.runtime().play(request, offsetMs).whenComplete((result, throwable) -> {
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
                        "Announcement packet rejected or skipped by playback manager: id={}, event={}, decision={}, reason={}, catchUp={}, offsetMs={}",
                        packet.announcementId(),
                        packet.eventType(),
                        result.decision(),
                        result.message(),
                        packet.catchUp(),
                        offsetMs
                );
                return;
            }

            PlaybackSession session = result.session();

            CreateRailwayAnnouncer.LOGGER.info(
                    "Announcement packet playback started: packetId={}, event={}, sequence={}, channel={}, priority={}, decision={}, catchUp={}, elapsedTicks={}, offsetMs={}, remainingTotalDurationMs={}",
                    packet.announcementId(),
                    packet.eventType(),
                    session.sequence().id(),
                    session.channel(),
                    session.sequence().priority(),
                    result.decision(),
                    packet.catchUp(),
                    packet.packetSendGameTime() - packet.announcementStartGameTime(),
                    offsetMs,
                    session.sequence().totalDurationMs()
            );
        });
    }

    private static long offsetMsFor(AnnouncementPacket packet) {
        if (packet == null || !packet.catchUp()) {
            return 0L;
        }

        long elapsedTicks = packet.packetSendGameTime() - packet.announcementStartGameTime();
        if (elapsedTicks <= 0L) {
            return 0L;
        }

        return elapsedTicks * 50L;
    }
}