package com.misaka10843.create_railway_announcer.client.runtime;

import com.misaka10843.create_railway_announcer.CreateRailwayAnnouncer;
import com.misaka10843.create_railway_announcer.client.ClientAnnouncementFilter;
import com.misaka10843.create_railway_announcer.client.ClientListenerContext;
import com.misaka10843.create_railway_announcer.client.ListenerPlace;
import com.misaka10843.create_railway_announcer.network.AnnouncementPacket;
import com.misaka10843.create_railway_announcer.playback.PlaybackSession;

public final class ClientAnnouncementPacketClientHandler {
    private static final java.util.LinkedHashSet<java.util.UUID> RECEIVED = new java.util.LinkedHashSet<>();
    public static void reset() { RECEIVED.clear(); }
    private ClientAnnouncementPacketClientHandler() {
    }

    public static void handle(AnnouncementPacket packet) {
        if(packet.channel()!=com.misaka10843.create_railway_announcer.announcement.AnnouncementSequenceMapper.channel(packet.eventType())) return;
        if (!com.misaka10843.create_railway_announcer.config.ClientConfig.ANNOUNCEMENTS_ENABLED.get()) return;
        if (!com.misaka10843.create_railway_announcer.config.AnnouncementClientData.enabled()) return;
        if (packet.channel() == com.misaka10843.create_railway_announcer.audio.AudioChannel.ONBOARD_VOICE
                && !com.misaka10843.create_railway_announcer.config.ClientConfig.ONBOARD_ENABLED.get()) return;
        if (packet.channel() == com.misaka10843.create_railway_announcer.audio.AudioChannel.PLATFORM_VOICE
                && !com.misaka10843.create_railway_announcer.config.ClientConfig.PLATFORM_ENABLED.get()) return;
        if (packet.channel() == com.misaka10843.create_railway_announcer.audio.AudioChannel.DOOR_CHIME
                && !com.misaka10843.create_railway_announcer.config.ClientConfig.DOOR_CHIMES_ENABLED.get()) return;
        if (RECEIVED.contains(packet.announcementId())) return;
        AnnouncementPlaybackRequest request = AnnouncementPlaybackRequest.fromPacket(packet);

        ListenerPlace place =
                ClientListenerContext.resolve(
                        packet.stationPos(),
                        packet.horizontalRange(),
                        packet.verticalRange(),
                        packet.trainId()
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
        RECEIVED.add(packet.announcementId());
        if (RECEIVED.size() > 256) RECEIVED.remove(RECEIVED.iterator().next());

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
