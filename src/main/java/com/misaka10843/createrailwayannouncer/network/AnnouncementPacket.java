package com.misaka10843.createrailwayannouncer.network;

import com.misaka10843.createrailwayannouncer.CreateRailwayAnnouncer;
import com.misaka10843.createrailwayannouncer.announcement.AnnouncementEventType;
import com.misaka10843.createrailwayannouncer.audio.AudioChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record AnnouncementPacket(
        UUID announcementId,
        AnnouncementEventType eventType,
        String sequenceId,
        UUID trainId,
        String trainName,
        UUID stationId,
        BlockPos stationPos,
        String currentStationConfigId,
        String nextStationConfigId,
        String destinationStationConfigId,
        int horizontalRange,
        int verticalRange,
        AudioChannel channel,
        int priority,
        long announcementStartGameTime,
        long packetSendGameTime,
        boolean catchUp
) implements CustomPacketPayload {
    public static final Type<AnnouncementPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateRailwayAnnouncer.MODID, "announcement")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, AnnouncementPacket> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public AnnouncementPacket decode(RegistryFriendlyByteBuf buffer) {
                    return read(buffer);
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer, AnnouncementPacket packet) {
                    write(buffer, packet);
                }
            };

    private static AnnouncementPacket read(RegistryFriendlyByteBuf buffer) {
        UUID announcementId = buffer.readUUID();
        AnnouncementEventType eventType = buffer.readEnum(AnnouncementEventType.class);
        String sequenceId = buffer.readUtf();
        UUID trainId = buffer.readUUID();
        String trainName = buffer.readUtf();
        UUID stationId = buffer.readUUID();
        BlockPos stationPos = buffer.readBlockPos();
        String currentStationConfigId = buffer.readUtf();
        String nextStationConfigId = buffer.readUtf();
        String destinationStationConfigId = buffer.readUtf();
        int horizontalRange = buffer.readVarInt();
        int verticalRange = buffer.readVarInt();
        AudioChannel channel = buffer.readEnum(AudioChannel.class);
        int priority = buffer.readVarInt();
        long announcementStartGameTime = buffer.readLong();
        long packetSendGameTime = buffer.readLong();
        boolean catchUp = buffer.readBoolean();

        return new AnnouncementPacket(
                announcementId,
                eventType,
                sequenceId,
                trainId,
                trainName,
                stationId,
                stationPos,
                currentStationConfigId,
                nextStationConfigId,
                destinationStationConfigId,
                horizontalRange,
                verticalRange,
                channel,
                priority,
                announcementStartGameTime,
                packetSendGameTime,
                catchUp
        );
    }

    private static void write(RegistryFriendlyByteBuf buffer, AnnouncementPacket packet) {
        buffer.writeUUID(packet.announcementId());
        buffer.writeEnum(packet.eventType());
        buffer.writeUtf(nullToEmpty(packet.sequenceId()));
        buffer.writeUUID(packet.trainId());
        buffer.writeUtf(nullToEmpty(packet.trainName()));
        buffer.writeUUID(packet.stationId());
        buffer.writeBlockPos(packet.stationPos());
        buffer.writeUtf(nullToEmpty(packet.currentStationConfigId()));
        buffer.writeUtf(nullToEmpty(packet.nextStationConfigId()));
        buffer.writeUtf(nullToEmpty(packet.destinationStationConfigId()));
        buffer.writeVarInt(packet.horizontalRange());
        buffer.writeVarInt(packet.verticalRange());
        buffer.writeEnum(packet.channel());
        buffer.writeVarInt(packet.priority());
        buffer.writeLong(packet.announcementStartGameTime());
        buffer.writeLong(packet.packetSendGameTime());
        buffer.writeBoolean(packet.catchUp());
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}