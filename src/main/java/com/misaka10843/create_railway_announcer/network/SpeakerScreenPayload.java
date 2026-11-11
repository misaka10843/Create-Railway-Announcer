package com.misaka10843.create_railway_announcer.network;

import com.misaka10843.create_railway_announcer.CreateRailwayAnnouncer;
import com.misaka10843.create_railway_announcer.announcement.*;
import com.misaka10843.create_railway_announcer.audio.AudioChannel;
import com.misaka10843.create_railway_signage.blockentity.*;
import com.misaka10843.create_railway_announcer.blockentity.RailwaySpeakerBlockEntity;
import com.misaka10843.create_railway_signage.data.RailwaySignageSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SpeakerScreenPayload(BlockPos pos, boolean onboard, String stationId, String platformId, int horizontal, int vertical,
                                   AnnouncementEventType event, boolean enabled, boolean canEdit) implements CustomPacketPayload {
    public SpeakerScreenPayload(BlockPos pos,boolean onboard,String station,String platform,int horizontal,int vertical,AnnouncementEventType event,boolean enabled) {
        this(pos,onboard,station,platform,horizontal,vertical,event,enabled,true);
    }
    public static final Type<SpeakerScreenPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CreateRailwayAnnouncer.MODID, "speaker_screen"));
    public static final StreamCodec<FriendlyByteBuf, SpeakerScreenPayload> STREAM_CODEC = StreamCodec.of((b,v) -> {
        b.writeBlockPos(v.pos); b.writeBoolean(v.onboard); b.writeUtf(v.stationId,64); b.writeUtf(v.platformId,128);
        b.writeVarInt(v.horizontal); b.writeVarInt(v.vertical); b.writeEnum(v.event); b.writeBoolean(v.enabled); b.writeBoolean(v.canEdit);
    }, b -> new SpeakerScreenPayload(b.readBlockPos(),b.readBoolean(),b.readUtf(64),b.readUtf(128),b.readVarInt(),b.readVarInt(),b.readEnum(AnnouncementEventType.class),b.readBoolean(),b.readBoolean()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void open(SpeakerScreenPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            try { Class.forName("com.misaka10843.create_railway_announcer.client.SpeakerUi").getMethod("open",SpeakerScreenPayload.class).invoke(null,payload); }
            catch (ReflectiveOperationException error) { CreateRailwayAnnouncer.LOGGER.error("Cannot open speaker editor",error); }
        });
    }
    private static boolean canEdit(ServerPlayer player, BlockPos pos) {
        return player.blockPosition().distSqr(pos) <= 144 && (!com.misaka10843.create_railway_announcer.config.ServerConfig.OP_REQUIRED_TO_EDIT_STATION.get() || player.hasPermissions(2));
    }
    public record Save(BlockPos pos, String platformId, int horizontal, int vertical, AnnouncementEventType event, boolean enabled) implements CustomPacketPayload {
        public static final Type<Save> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CreateRailwayAnnouncer.MODID,"save_speaker"));
        public static final StreamCodec<FriendlyByteBuf,Save> STREAM_CODEC = StreamCodec.of((b,v) -> {
            b.writeBlockPos(v.pos); b.writeUtf(v.platformId,128); b.writeVarInt(v.horizontal); b.writeVarInt(v.vertical); b.writeEnum(v.event); b.writeBoolean(v.enabled);
        }, b -> new Save(b.readBlockPos(),b.readUtf(128),b.readVarInt(),b.readVarInt(),b.readEnum(AnnouncementEventType.class),b.readBoolean()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
        public static void handle(Save payload, IPayloadContext context) {
            context.enqueueWork(() -> {
                if (!(context.player() instanceof ServerPlayer player)) return;
                if (!canEdit(player,payload.pos)) { player.sendSystemMessage(Component.translatable("announcer.create_railway_announcer.denied")); return; }
                if (!(player.serverLevel().getBlockEntity(payload.pos) instanceof RailwaySpeakerBlockEntity speaker)) return;
                var group = RailwaySignageSavedData.get(player.getServer()).stationGroup(speaker.stationId()).orElse(null);
                if (!speaker.onboard() && (group == null || !payload.platformId.isBlank() && group.platform(payload.platformId).isEmpty())) {
                    player.sendSystemMessage(Component.translatable("announcer.create_railway_announcer.no_stations")); return;
                }
                String platform = group == null || payload.platformId.isBlank() ? "" : group.platform(payload.platformId).orElseThrow().platformId();
                if (!platform.isBlank() && !group.platform(platform).orElseThrow().dimension().equals(player.level().dimension())) {
                    player.sendSystemMessage(Component.translatable("announcer.create_railway_announcer.invalid")); return;
                }
                try { speaker.configure(platform,payload.horizontal,payload.vertical,payload.event,payload.enabled);
                    player.sendSystemMessage(Component.translatable("announcer.create_railway_announcer.speaker_saved")); }
                catch (IllegalArgumentException error) { player.sendSystemMessage(Component.translatable("announcer.create_railway_announcer.invalid")); }
            });
        }
    }
    public record Test(BlockPos pos, String platformId, AnnouncementEventType event) implements CustomPacketPayload {
        public static final Type<Test> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CreateRailwayAnnouncer.MODID,"test_speaker"));
        public static final StreamCodec<FriendlyByteBuf,Test> STREAM_CODEC = StreamCodec.of((b,v)-> {
            b.writeBlockPos(v.pos);b.writeUtf(v.platformId,128);b.writeEnum(v.event);
        },b->new Test(b.readBlockPos(),b.readUtf(128),b.readEnum(AnnouncementEventType.class)));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
        public static void handle(Test payload, IPayloadContext context) {
            context.enqueueWork(() -> {
                if (!(context.player() instanceof ServerPlayer player)) return;
                if (!canEdit(player,payload.pos)) { player.sendSystemMessage(Component.translatable("announcer.create_railway_announcer.denied")); return; }
                var block = player.serverLevel().getBlockEntity(payload.pos);
                if (block instanceof RailwaySpeakerBlockEntity speaker) { speaker.trigger(player); return; }
                if (!(block instanceof StationControllerBlockEntity controller)) return;
                var group = RailwaySignageSavedData.get(player.getServer()).stationGroup(controller.stationGroupId()).orElse(null);
                var binding = group == null ? null : group.platform(payload.platformId).orElse(null);
                if (binding == null) return;
                if (!AnnouncementSequenceMapper.manualEvents().contains(payload.event)) return;
                String id = group.id()+"/"+binding.platformId();
                boolean onboard = AnnouncementSequenceMapper.channel(payload.event) == AudioChannel.ONBOARD_VOICE;
                var train = onboard
                        ? com.misaka10843.create_railway_announcer.compat.create.Create6TrainAdapter.trainAtStation(binding.createStationId()).orElse(null) : null;
                if (onboard && train == null) {
                    player.sendSystemMessage(Component.translatable("announcer.create_railway_announcer.no_train")); return;
                }
                var request = ServerAnnouncementRequest.of(payload.event,train == null ? null : train.trainId(),train == null ? "" : train.trainName(),
                        binding.createStationId(),binding.stationBlockPos(),id,id,id,AnnouncementSequenceMapper.channel(payload.event),80);
                var result = ServerAnnouncementDispatcher.dispatchToNearbyPlayers(player.serverLevel(),request);
                player.sendSystemMessage(result.feedback());
            });
        }
    }
}
