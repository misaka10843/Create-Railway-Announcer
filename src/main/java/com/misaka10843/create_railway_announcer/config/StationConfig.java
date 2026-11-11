package com.misaka10843.create_railway_announcer.config;

import net.minecraft.core.BlockPos;

import java.util.*;

public class StationConfig {
    private com.misaka10843.create_railway_announcer.model.StationAnnouncementSettings announcements = com.misaka10843.create_railway_announcer.model.StationAnnouncementSettings.DEFAULT;
    public com.misaka10843.create_railway_announcer.model.StationAnnouncementSettings announcements() { return announcements; }
    public void setAnnouncements(com.misaka10843.create_railway_announcer.model.StationAnnouncementSettings value) { announcements = value; }
    private final Map<String, String> display = new HashMap<>();
    private final Map<String, String> reading = new HashMap<>();
    private final List<String> transferLineIds = new ArrayList<>();
    private UUID createStationId;
    private String customId = "";
    private boolean enabled = true;
    private DoorSide doorSide = DoorSide.NONE;
    private String platform = "";
    private int horizontalRange = ServerConfig.DEFAULT_STATION_HORIZONTAL_RANGE.getDefault();
    private int verticalRange = ServerConfig.DEFAULT_STATION_VERTICAL_RANGE.getDefault();
    private String dimension = "";
    private BlockPos position;

    public UUID getCreateStationId() {
        return createStationId;
    }

    public void setCreateStationId(UUID createStationId) {
        this.createStationId = createStationId;
    }

    public String getCustomId() {
        return customId;
    }

    public void setCustomId(String customId) {
        this.customId = customId == null ? "" : customId;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Map<String, String> getDisplay() {
        return display;
    }

    public Map<String, String> getReading() {
        return reading;
    }

    public DoorSide getDoorSide() {
        return doorSide;
    }

    public void setDoorSide(DoorSide doorSide) {
        this.doorSide = doorSide == null ? DoorSide.NONE : doorSide;
    }

    public String getPlatform() {
        return platform;
    }

    public void setPlatform(String platform) {
        this.platform = platform == null ? "" : platform;
    }

    public int getHorizontalRange() {
        return horizontalRange;
    }

    public void setHorizontalRange(int horizontalRange) {
        this.horizontalRange = horizontalRange;
    }

    public int getVerticalRange() {
        return verticalRange;
    }

    public void setVerticalRange(int verticalRange) {
        this.verticalRange = verticalRange;
    }

    public List<String> getTransferLineIds() {
        return transferLineIds;
    }

    public String getDimension() {
        return dimension;
    }

    public void setDimension(String dimension) {
        this.dimension = dimension == null ? "" : dimension;
    }

    public BlockPos getPosition() {
        return position;
    }

    public void setPosition(BlockPos position) {
        this.position = position;
    }

    public boolean hasPosition() {
        return position != null && dimension != null && !dimension.isBlank();
    }
}
