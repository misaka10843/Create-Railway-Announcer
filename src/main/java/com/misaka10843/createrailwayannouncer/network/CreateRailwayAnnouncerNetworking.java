package com.misaka10843.createrailwayannouncer.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class CreateRailwayAnnouncerNetworking {
    private static final String NETWORK_VERSION = "1";

    private CreateRailwayAnnouncerNetworking() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(NETWORK_VERSION);

        registrar.playToClient(
                AnnouncementPacket.TYPE,
                AnnouncementPacket.STREAM_CODEC,
                ClientboundAnnouncementPacketHandler::handle
        );
    }
}