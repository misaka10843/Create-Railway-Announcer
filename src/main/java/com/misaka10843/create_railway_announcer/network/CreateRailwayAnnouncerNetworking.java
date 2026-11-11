package com.misaka10843.create_railway_announcer.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class CreateRailwayAnnouncerNetworking {
    private static final String NETWORK_VERSION = "4";

    private CreateRailwayAnnouncerNetworking() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(NETWORK_VERSION);
        registrar.playToClient(StopPlaybackPayload.TYPE,StopPlaybackPayload.STREAM_CODEC,StopPlaybackPayload::handle);
        registrar.playToServer(ControllerAudioPayload.Stop.TYPE,ControllerAudioPayload.Stop.STREAM_CODEC,ControllerAudioPayload.Stop::handle);
        registrar.playToClient(AnnouncementDataPayload.TYPE,AnnouncementDataPayload.STREAM_CODEC,AnnouncementDataPayload::handle);
        registrar.playToClient(ControllerAudioPayload.TYPE,ControllerAudioPayload.STREAM_CODEC,ControllerAudioPayload::open);
        registrar.playToServer(ControllerAudioPayload.Request.TYPE,ControllerAudioPayload.Request.STREAM_CODEC,ControllerAudioPayload.Request::handle);
        registrar.playToServer(ControllerAudioPayload.Save.TYPE,ControllerAudioPayload.Save.STREAM_CODEC,ControllerAudioPayload.Save::handle);

        registrar.playToClient(
                AnnouncementPacket.TYPE,
                AnnouncementPacket.STREAM_CODEC,
                ClientboundAnnouncementPacketHandler::handle
        );
        registrar.playToClient(SpeakerScreenPayload.TYPE,SpeakerScreenPayload.STREAM_CODEC,SpeakerScreenPayload::open);
        registrar.playToServer(SpeakerScreenPayload.Save.TYPE,SpeakerScreenPayload.Save.STREAM_CODEC,SpeakerScreenPayload.Save::handle);
        registrar.playToServer(SpeakerScreenPayload.Test.TYPE,SpeakerScreenPayload.Test.STREAM_CODEC,SpeakerScreenPayload.Test::handle);
    }
}
