package com.misaka10843.createrailwayannouncer.config;

import net.minecraft.core.BlockPos;

import java.util.HashMap;
import java.util.Map;

public class PlatformConfig {

    private String id = "";

    private String stationId = "";

    private String lineId = "";

    private final Map<String, String> display = new HashMap<>();

    private final Map<String, String> reading = new HashMap<>();

    private DoorSide doorSide = DoorSide.NONE;

    private BlockPos position;

    private int horizontalRange = 16;

    private int verticalRange = 8;


    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id == null ? "" : id;
    }


    public String getStationId() {
        return stationId;
    }

    public void setStationId(String stationId) {
        this.stationId = stationId == null ? "" : stationId;
    }


    public String getLineId() {
        return lineId;
    }

    public void setLineId(String lineId) {
        this.lineId = lineId == null ? "" : lineId;
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
        this.doorSide = doorSide == null
                ? DoorSide.NONE
                : doorSide;
    }


    public BlockPos getPosition() {
        return position;
    }


    public void setPosition(BlockPos position) {
        this.position = position;
    }


    public boolean hasPosition() {
        return position != null;
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
}