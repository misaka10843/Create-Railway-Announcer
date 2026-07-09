package com.misaka10843.createrailwayannouncer.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class StationConfig {
    private UUID createStationId;
    private String customId = "";
    private boolean enabled = true;
    private final Map<String, String> display = new HashMap<>();
    private final Map<String, String> reading = new HashMap<>();
    private DoorSide doorSide = DoorSide.NONE;
    private String platform = "1";
    private int horizontalRange = ServerConfig.DEFAULT_STATION_HORIZONTAL_RANGE.get();
    private int verticalRange = ServerConfig.DEFAULT_STATION_VERTICAL_RANGE.get();
    private final List<String> transferLineIds = new ArrayList<>();

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
}
