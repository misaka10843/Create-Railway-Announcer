package com.misaka10843.create_railway_announcer.blockentity;

import com.misaka10843.create_railway_announcer.announcement.*;
import com.misaka10843.create_railway_announcer.announcement.source.ServerAnnouncementSourceRegistry;
import com.misaka10843.create_railway_announcer.audio.AudioChannel;
import com.misaka10843.create_railway_announcer.block.RailwaySpeakerBlock;
import com.misaka10843.create_railway_signage.data.*;
import com.misaka10843.create_railway_announcer.registry.ModBlockEntities;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import java.util.UUID;

public final class RailwaySpeakerBlockEntity extends BlockEntity {
    private String stationId = "", platformId = "";
    private int horizontalRange = 32, verticalRange = 12;
    private AnnouncementEventType event = AnnouncementEventType.PLATFORM_DOOR_CLOSING;
    public RailwaySpeakerBlockEntity(BlockPos pos, BlockState state) { super(ModBlockEntities.RAILWAY_SPEAKER.get(), pos, state); }
    public boolean onboard() { return ((RailwaySpeakerBlock)getBlockState().getBlock()).onboard(); }
    public String stationId() { return stationId; }
    public String platformId() { return platformId; }
    public int horizontalRange() { return horizontalRange; }
    public int verticalRange() { return verticalRange; }
    public AnnouncementEventType event() { return event; }
    private UUID sourceId() { return UUID.nameUUIDFromBytes((level.dimension().location() + ":" + worldPosition.asLong()).getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
    public void bindNearest() {
        if (!(level instanceof ServerLevel server) || onboard()) return;
        if (stationId.isBlank() || RailwaySignageSavedData.get(server.getServer()).stationGroup(stationId).isEmpty()) {
            var station = StationGroupLocator.nearest(server, worldPosition);
            if (station != null) { stationId = station.id(); setChanged(); }
        }
        registerSource();
    }
    public void configure(String platformId, int horizontal, int vertical, AnnouncementEventType event, boolean enabled) {
        if (horizontal < 4 || horizontal > 128 || vertical < 2 || vertical > 64) throw new IllegalArgumentException("Speaker range is invalid");
        if (AnnouncementSequenceMapper.channel(event)!=AudioChannel.PLATFORM_VOICE && AnnouncementSequenceMapper.channel(event)!=AudioChannel.MELODY) throw new IllegalArgumentException("Invalid station speaker event");
        this.platformId = platformId.trim(); horizontalRange = horizontal; verticalRange = vertical; this.event = event;
        level.setBlock(worldPosition, getBlockState().setValue(RailwaySpeakerBlock.ENABLED, enabled), 3);
        setChanged(); registerSource();
    }
    private void registerSource() {
        if (!(level instanceof ServerLevel server) || onboard()) return;
        ServerAnnouncementSourceRegistry.remove(sourceId());
        if (!stationId.isBlank() && getBlockState().getValue(RailwaySpeakerBlock.ENABLED))
            ServerAnnouncementSourceRegistry.stationSpeaker(server, sourceId(), stationId + "/" + platformId, worldPosition, horizontalRange, verticalRange);
    }
    @Override public void onLoad() { super.onLoad(); registerSource(); }
    @Override public void onChunkUnloaded() {
        if (level instanceof ServerLevel && !onboard()) ServerAnnouncementSourceRegistry.remove(sourceId());
        super.onChunkUnloaded();
    }
    @Override public void setRemoved() {
        if (level instanceof ServerLevel && !onboard()) ServerAnnouncementSourceRegistry.remove(sourceId());
        super.setRemoved();
    }
    public void open(ServerPlayer player) {
        bindNearest();
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                new com.misaka10843.create_railway_announcer.network.SpeakerScreenPayload(worldPosition, onboard(), stationId, platformId,
                        horizontalRange, verticalRange, event, getBlockState().getValue(RailwaySpeakerBlock.ENABLED),
                        !com.misaka10843.create_railway_announcer.config.ServerConfig.OP_REQUIRED_TO_EDIT_STATION.get() || player.hasPermissions(2)));
    }
    public void trigger(ServerPlayer player) {
        if (!(level instanceof ServerLevel server) || onboard()) return;
        if (!getBlockState().getValue(RailwaySpeakerBlock.ENABLED)) {
            if (player != null) player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("announcer.create_railway_announcer.speaker_muted"));
            return;
        }
        bindNearest();
        var group = RailwaySignageSavedData.get(server.getServer()).stationGroup(stationId).orElse(null);
        var binding = group == null ? null : group.platforms().stream().filter(value -> value.dimension().equals(server.dimension()))
                .filter(value -> platformId.isBlank() || value.platformId().equalsIgnoreCase(platformId)).findFirst().orElse(null);
        if (binding == null) return;
        String id = stationId + "/" + binding.platformId();
        var request = ServerAnnouncementRequest.of(event, null, "", binding.createStationId(), worldPosition, id, id, id, AnnouncementSequenceMapper.channel(event), 80);
        var result = ServerAnnouncementDispatcher.dispatchToNearbyPlayers(server, request);
        if (player != null) player.sendSystemMessage(result.feedback());
    }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries); tag.putString("StationId", stationId); tag.putString("PlatformId", platformId);
        tag.putInt("HorizontalRange", horizontalRange); tag.putInt("VerticalRange", verticalRange); tag.putString("Event", event.name());
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries); stationId = tag.getString("StationId"); platformId = tag.getString("PlatformId");
        horizontalRange = tag.contains("HorizontalRange") ? Math.clamp(tag.getInt("HorizontalRange"),4,128) : 32;
        verticalRange = tag.contains("VerticalRange") ? Math.clamp(tag.getInt("VerticalRange"),2,64) : 12;
        try { event = AnnouncementEventType.valueOf(tag.getString("Event")); } catch (IllegalArgumentException ignored) { event = AnnouncementEventType.PLATFORM_DOOR_CLOSING; }
        if (AnnouncementSequenceMapper.channel(event)!=AudioChannel.PLATFORM_VOICE && AnnouncementSequenceMapper.channel(event)!=AudioChannel.MELODY) event = AnnouncementEventType.PLATFORM_DOOR_CLOSING;
    }
}
