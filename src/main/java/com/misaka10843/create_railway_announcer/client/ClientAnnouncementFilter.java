package com.misaka10843.create_railway_announcer.client;

import com.misaka10843.create_railway_announcer.audio.AudioChannel;
import com.misaka10843.create_railway_announcer.network.AnnouncementPacket;

public final class ClientAnnouncementFilter {

    private ClientAnnouncementFilter() {
    }


    public static boolean shouldPlay(
            AnnouncementPacket packet,
            ListenerPlace place
    ) {
        if (packet == null) {
            return false;
        }

        return switch (packet.channel()) {

            case ONBOARD_VOICE ->
                    place == ListenerPlace.ON_RELATED_TRAIN;

            case PLATFORM_VOICE ->
                    place == ListenerPlace.IN_STATION_AREA;

            case MELODY ->
                    place == ListenerPlace.IN_STATION_AREA;

            case DOOR_CHIME ->
                    place != ListenerPlace.OUTSIDE;

            case AMBIENT, UI_PREVIEW ->
                    true;
        };
    }
}
