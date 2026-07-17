package com.misaka10843.createrailwayannouncer.announcement;

import com.misaka10843.createrailwayannouncer.audio.AudioChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class ServerActiveAnnouncementSession {
    private final UUID announcementId;
    private final ResourceLocation levelId;
    private final AnnouncementEventType eventType;
    private final UUID trainId;
    private final String trainName;
    private final UUID stationId;
    private final BlockPos stationPos;
    private final String currentStationConfigId;
    private final String nextStationConfigId;
    private final String destinationStationConfigId;
    private final int horizontalRange;
    private final int verticalRange;
    private final AudioChannel channel;
    private final int priority;
    private final long startGameTime;
    private final long expireGameTime;
    private final Set<UUID> sentPlayerIds = new HashSet<>();

    public ServerActiveAnnouncementSession(
            UUID announcementId,
            ResourceLocation levelId,
            AnnouncementEventType eventType,
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
            long startGameTime,
            long expireGameTime,
            Set<UUID> initiallySentPlayerIds
    ) {
        this.announcementId = announcementId;
        this.levelId = levelId;
        this.eventType = eventType;
        this.trainId = trainId;
        this.trainName = trainName == null ? "" : trainName;
        this.stationId = stationId;
        this.stationPos = stationPos;
        this.currentStationConfigId = currentStationConfigId == null ? "" : currentStationConfigId;
        this.nextStationConfigId = nextStationConfigId == null ? "" : nextStationConfigId;
        this.destinationStationConfigId = destinationStationConfigId == null ? "" : destinationStationConfigId;
        this.horizontalRange = horizontalRange;
        this.verticalRange = verticalRange;
        this.channel = channel;
        this.priority = priority;
        this.startGameTime = startGameTime;
        this.expireGameTime = expireGameTime;

        if (initiallySentPlayerIds != null) {
            this.sentPlayerIds.addAll(initiallySentPlayerIds);
        }
    }

    public UUID announcementId() {
        return announcementId;
    }

    public ResourceLocation levelId() {
        return levelId;
    }

    public AnnouncementEventType eventType() {
        return eventType;
    }

    public UUID trainId() {
        return trainId;
    }

    public String trainName() {
        return trainName;
    }

    public UUID stationId() {
        return stationId;
    }

    public BlockPos stationPos() {
        return stationPos;
    }

    public String currentStationConfigId() {
        return currentStationConfigId;
    }

    public String nextStationConfigId() {
        return nextStationConfigId;
    }

    public String destinationStationConfigId() {
        return destinationStationConfigId;
    }

    public int horizontalRange() {
        return horizontalRange;
    }

    public int verticalRange() {
        return verticalRange;
    }

    public AudioChannel channel() {
        return channel;
    }

    public int priority() {
        return priority;
    }

    public long startGameTime() {
        return startGameTime;
    }

    public long expireGameTime() {
        return expireGameTime;
    }

    public boolean isExpired(long gameTime) {
        return gameTime >= expireGameTime;
    }

    public boolean hasSentTo(UUID playerId) {
        return sentPlayerIds.contains(playerId);
    }

    public void markSent(UUID playerId) {
        sentPlayerIds.add(playerId);
    }

    public int sentPlayerCount() {
        return sentPlayerIds.size();
    }
}