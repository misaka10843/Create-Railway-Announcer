package com.misaka10843.createrailwayannouncer.config;

import java.util.HashMap;
import java.util.Map;

public class LineConfig {
    private final Map<String, String> display = new HashMap<>();
    private final Map<String, String> reading = new HashMap<>();
    private final Map<String, String> shortName = new HashMap<>();
    private String id = "";

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id == null ? "" : id;
    }

    public Map<String, String> getDisplay() {
        return display;
    }

    public Map<String, String> getReading() {
        return reading;
    }

    public Map<String, String> getShortName() {
        return shortName;
    }
}
