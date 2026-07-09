package com.misaka10843.createrailwayannouncer.network;

import com.misaka10843.createrailwayannouncer.CreateRailwayAnnouncer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.lang.reflect.Method;

public final class ClientboundAnnouncementPacketHandler {
    private ClientboundAnnouncementPacketHandler() {
    }

    public static void handle(AnnouncementPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            CreateRailwayAnnouncer.LOGGER.info(
                    "Received clientbound announcement packet: id={}, event={}, train={}, station={}, channel={}, priority={}",
                    packet.announcementId(),
                    packet.eventType(),
                    packet.trainName(),
                    packet.nextStationConfigId(),
                    packet.channel(),
                    packet.priority()
            );

            invokeClientHandler(packet);
        });
    }

    private static void invokeClientHandler(AnnouncementPacket packet) {
        try {
            Class<?> clazz = Class.forName(
                    "com.misaka10843.createrailwayannouncer.client.runtime.ClientAnnouncementPacketClientHandler"
            );

            Method method = clazz.getMethod("handle", AnnouncementPacket.class);
            method.invoke(null, packet);
        } catch (Throwable t) {
            CreateRailwayAnnouncer.LOGGER.error(
                    "Failed to invoke client announcement packet handler: id={}, event={}",
                    packet.announcementId(),
                    packet.eventType(),
                    t
            );
        }
    }
}