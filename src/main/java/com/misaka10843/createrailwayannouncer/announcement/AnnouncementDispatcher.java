package com.misaka10843.createrailwayannouncer.announcement;

import com.misaka10843.createrailwayannouncer.CreateRailwayAnnouncer;

public final class AnnouncementDispatcher {
    private AnnouncementDispatcher() {
    }

    public static void dispatchServerEvent(AnnouncementEvent event) {
        CreateRailwayAnnouncer.LOGGER.debug("Announcement event: {} train={} station={}", event.type(), event.trainId(), event.stationId());
    }
}
