package com.misaka10843.createrailwayannouncer.client;

import com.misaka10843.createrailwayannouncer.audio.AudioChannel;
import com.misaka10843.createrailwayannouncer.network.AnnouncementPacket;

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
                    place == ListenerPlace.ON_RELATED_TRAIN
                            || place == ListenerPlace.ON_OTHER_TRAIN
                            || place == ListenerPlace.IN_STATION_AREA;

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