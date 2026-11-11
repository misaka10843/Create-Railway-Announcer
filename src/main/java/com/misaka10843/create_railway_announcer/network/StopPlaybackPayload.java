package com.misaka10843.create_railway_announcer.network;

import com.misaka10843.create_railway_announcer.audio.AudioChannel;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record StopPlaybackPayload(String stationId,AudioChannel channel) implements CustomPacketPayload {
    public static final Type<StopPlaybackPayload> TYPE=ControllerAudioPayload.type("stop_playback");
    public static final StreamCodec<FriendlyByteBuf,StopPlaybackPayload> STREAM_CODEC=StreamCodec.of((b,v)->{b.writeUtf(v.stationId,256);b.writeBoolean(v.channel!=null);if(v.channel!=null)b.writeEnum(v.channel);},b->new StopPlaybackPayload(b.readUtf(256),b.readBoolean()?b.readEnum(AudioChannel.class):null));
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    public static void handle(StopPlaybackPayload payload,net.neoforged.neoforge.network.handling.IPayloadContext context){context.enqueueWork(()->{
        try{Class.forName("com.misaka10843.create_railway_announcer.client.runtime.ClientAnnouncementRuntime").getMethod("stopStation",String.class,AudioChannel.class).invoke(null,payload.stationId,payload.channel);}
        catch(ReflectiveOperationException error){com.misaka10843.create_railway_announcer.CreateRailwayAnnouncer.LOGGER.error("Cannot stop station playback",error);}
    });}
}
