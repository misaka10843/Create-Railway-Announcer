package com.misaka10843.create_railway_announcer.network;

import com.misaka10843.create_railway_announcer.CreateRailwayAnnouncer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record AnnouncementDataPayload(CompoundTag data) implements CustomPacketPayload {
    public static final Type<AnnouncementDataPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(CreateRailwayAnnouncer.MODID,"data"));
    public static final StreamCodec<FriendlyByteBuf,AnnouncementDataPayload> STREAM_CODEC=StreamCodec.of((b,v)->b.writeNbt(v.data),b->new AnnouncementDataPayload(java.util.Objects.requireNonNull(b.readNbt())));
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    public static void handle(AnnouncementDataPayload payload,net.neoforged.neoforge.network.handling.IPayloadContext context){
        context.enqueueWork(()->{
            com.misaka10843.create_railway_announcer.config.AnnouncementClientData.apply(payload.data);
            try {Class.forName("com.misaka10843.create_railway_announcer.client.AnnouncerClientEvents").getMethod("refreshProjection").invoke(null);}
            catch(ReflectiveOperationException error){CreateRailwayAnnouncer.LOGGER.error("Cannot refresh announcement projection",error);}
        });
    }
}
