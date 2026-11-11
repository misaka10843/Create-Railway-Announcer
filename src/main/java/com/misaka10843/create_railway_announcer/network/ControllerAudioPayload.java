package com.misaka10843.create_railway_announcer.network;

import com.misaka10843.create_railway_announcer.CreateRailwayAnnouncer;
import com.misaka10843.create_railway_announcer.config.ServerConfig;
import com.misaka10843.create_railway_announcer.data.AnnouncementSavedData;
import com.misaka10843.create_railway_announcer.model.StationAnnouncementSettings;
import com.misaka10843.create_railway_signage.blockentity.StationControllerBlockEntity;
import com.misaka10843.create_railway_signage.data.RailwaySignageSavedData;
import com.misaka10843.create_railway_signage.network.PlatformEditData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.util.*;

public record ControllerAudioPayload(BlockPos pos,String stationId,String name,List<PlatformEditData> platforms,StationAnnouncementSettings settings,
                                     boolean enabled,boolean admin,boolean editable) implements CustomPacketPayload {
    public static final Type<ControllerAudioPayload> TYPE=type("controller_audio");
    public static final StreamCodec<FriendlyByteBuf,ControllerAudioPayload> STREAM_CODEC=StreamCodec.of((b,v)->{
        b.writeBlockPos(v.pos);b.writeUtf(v.stationId,64);b.writeUtf(v.name,128);b.writeVarInt(v.platforms.size());v.platforms.forEach(p->p.write(b));
        b.writeNbt(v.settings.save());b.writeBoolean(v.enabled);b.writeBoolean(v.admin);b.writeBoolean(v.editable);
    },b->{var pos=b.readBlockPos();var id=b.readUtf(64);var name=b.readUtf(128);int count=b.readVarInt();if(count<0||count>256)throw new IllegalArgumentException("Too many platforms");
        var platforms=new ArrayList<PlatformEditData>();for(int i=0;i<count;i++)platforms.add(PlatformEditData.read(b));
        return new ControllerAudioPayload(pos,id,name,List.copyOf(platforms),StationAnnouncementSettings.load(Objects.requireNonNull(b.readNbt())),b.readBoolean(),b.readBoolean(),b.readBoolean());});
    static <T extends CustomPacketPayload> Type<T> type(String id){return new Type<>(ResourceLocation.fromNamespaceAndPath(CreateRailwayAnnouncer.MODID,id));}
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    private static StationControllerBlockEntity controller(ServerPlayer player,BlockPos pos){
        return player.blockPosition().distSqr(pos)<=144 && player.serverLevel().getBlockEntity(pos) instanceof StationControllerBlockEntity value?value:null;
    }
    public static void open(ControllerAudioPayload payload,IPayloadContext context){context.enqueueWork(()->{
        try {Class.forName("com.misaka10843.create_railway_announcer.client.ControllerAudioUi").getMethod("open",ControllerAudioPayload.class).invoke(null,payload);}
        catch(ReflectiveOperationException error){CreateRailwayAnnouncer.LOGGER.error("Cannot open station audio",error);}
    });}
    public record Request(BlockPos pos) implements CustomPacketPayload {
        public static final Type<Request> TYPE=ControllerAudioPayload.type("request_controller_audio");
        public static final StreamCodec<FriendlyByteBuf,Request> STREAM_CODEC=StreamCodec.of((b,v)->b.writeBlockPos(v.pos),b->new Request(b.readBlockPos()));
        @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
        public static void handle(Request payload,IPayloadContext context){context.enqueueWork(()->{
            if(!(context.player() instanceof ServerPlayer player))return;var controller=controller(player,payload.pos);if(controller==null)return;
            var group=RailwaySignageSavedData.get(player.getServer()).stationGroup(controller.stationGroupId()).orElse(null);
            if(group==null){player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("announcer.create_railway_announcer.no_stations"));return;}
            var data=AnnouncementSavedData.get(player.getServer());
            var platforms=group.platforms().stream().map(p->new PlatformEditData(p.platformId(),p.createStationId(),p.createStationName(),p.lineId(),"",p.destinationJapaneseName(),p.destinationRomanizedName(),p.destinationChineseName())).toList();
            PacketDistributor.sendToPlayer(player,new ControllerAudioPayload(payload.pos,group.id(),group.japaneseName(),platforms,data.station(group.id()),data.enabled(),player.hasPermissions(2),!ServerConfig.OP_REQUIRED_TO_EDIT_STATION.get()||player.hasPermissions(2)));
        });}
    }
    public record Save(BlockPos pos,String stationId,StationAnnouncementSettings settings,Boolean global) implements CustomPacketPayload {
        public static final Type<Save> TYPE=ControllerAudioPayload.type("save_controller_audio");
        public static final StreamCodec<FriendlyByteBuf,Save> STREAM_CODEC=StreamCodec.of((b,v)->{b.writeBlockPos(v.pos);b.writeUtf(v.stationId,64);b.writeNbt(v.settings.save());b.writeBoolean(v.global!=null);if(v.global!=null)b.writeBoolean(v.global);},
                b->new Save(b.readBlockPos(),b.readUtf(64),StationAnnouncementSettings.load(Objects.requireNonNull(b.readNbt())),b.readBoolean()?b.readBoolean():null));
        @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
        public static void handle(Save payload,IPayloadContext context){context.enqueueWork(()->{
            if(!(context.player() instanceof ServerPlayer player))return;var controller=controller(player,payload.pos);
            if(controller==null||!controller.stationGroupId().equals(payload.stationId))return;
            if(ServerConfig.OP_REQUIRED_TO_EDIT_STATION.get()&&!player.hasPermissions(2)||payload.global!=null&&!player.hasPermissions(2)){
                player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("announcer.create_railway_announcer.denied"));return;}
            var group=RailwaySignageSavedData.get(player.getServer()).stationGroup(payload.stationId).orElse(null);if(group==null)return;
            if(payload.settings.doors().keySet().stream().anyMatch(id->group.platform(id).isEmpty()))return;
            AnnouncementSavedData.get(player.getServer()).set(payload.stationId,payload.settings,payload.global);
            StationLineRefresh.refresh(player);player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("announcer.create_railway_announcer.saved"));
        });}
    }
    private static final class StationLineRefresh {
        static void refresh(ServerPlayer player){com.misaka10843.create_railway_announcer.config.StationLineConfigStore.refresh(player.getServer());AnnouncementSavedData.syncIfChanged(player.getServer());}
    }
    public record Stop(BlockPos pos,String platform) implements CustomPacketPayload {
        public static final Type<Stop> TYPE=ControllerAudioPayload.type("stop_station_audio");
        public static final StreamCodec<FriendlyByteBuf,Stop> STREAM_CODEC=StreamCodec.of((b,v)->{b.writeBlockPos(v.pos);b.writeUtf(v.platform,128);},b->new Stop(b.readBlockPos(),b.readUtf(128)));
        @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
        public static void handle(Stop payload,IPayloadContext context){context.enqueueWork(()->{
            if(!(context.player() instanceof ServerPlayer player))return;var controller=controller(player,payload.pos);
            if(controller==null||ServerConfig.OP_REQUIRED_TO_EDIT_STATION.get()&&!player.hasPermissions(2))return;
            var group=RailwaySignageSavedData.get(player.getServer()).stationGroup(controller.stationGroupId()).orElse(null);if(group==null||group.platform(payload.platform).isEmpty())return;
            com.misaka10843.create_railway_announcer.announcement.ServerActiveAnnouncementRegistry.stop(group.id()+"/"+group.platform(payload.platform).orElseThrow().platformId(),null);
        });}
    }
}
