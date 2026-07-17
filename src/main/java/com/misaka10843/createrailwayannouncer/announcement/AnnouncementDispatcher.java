package com.misaka10843.createrailwayannouncer.announcement;

import com.misaka10843.createrailwayannouncer.CreateRailwayAnnouncer;
import net.minecraft.server.level.ServerLevel;

public final class AnnouncementDispatcher {
    private AnnouncementDispatcher() {
    }

    public static void dispatchServerEvent(ServerLevel level, AnnouncementEvent event) {
        CreateRailwayAnnouncer.LOGGER.info(
                "Dispatch train announcement event: type={}, train={}, station={}",
                event.type(),
                event.trainId(),
                event.stationId()
        );

        ServerAnnouncementDispatcher.dispatch(level, event);
    }
}