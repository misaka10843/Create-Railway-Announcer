package com.misaka10843.create_railway_announcer.audio;

public enum AudioFragmentType {
    COMMON("common"),
    STATION("stations"),
    LINE("lines"),
    RAW("raw");

    private final String directoryName;

    AudioFragmentType(String directoryName) {
        this.directoryName = directoryName;
    }

    public String directoryName() {
        return directoryName;
    }
}