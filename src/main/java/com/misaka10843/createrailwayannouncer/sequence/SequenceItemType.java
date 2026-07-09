package com.misaka10843.createrailwayannouncer.sequence;

public enum SequenceItemType {
    SOUND,
    PAUSE,
    PHRASE,
    STATION,
    LINE,
    TRANSFER_LINES,
    DOOR_SIDE_PHRASE,
    SUBTITLE,
    CONDITION,
    TTS_RAW;

    public static SequenceItemType fromString(String value) {
        return switch (value.toLowerCase()) {
            case "sound" -> SOUND;
            case "pause" -> PAUSE;
            case "phrase" -> PHRASE;
            case "station" -> STATION;
            case "line" -> LINE;
            case "transfer_lines" -> TRANSFER_LINES;
            case "door_side_phrase" -> DOOR_SIDE_PHRASE;
            case "subtitle" -> SUBTITLE;
            case "condition" -> CONDITION;
            case "tts_raw" -> TTS_RAW;
            default -> throw new IllegalArgumentException("Unknown sequence item type: " + value);
        };
    }
}