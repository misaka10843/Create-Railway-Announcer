package com.misaka10843.createrailwayannouncer.audio;

import java.nio.file.Path;

public record AudioFragmentResult(
        boolean ok,
        Path audioPath,
        String format,
        long durationMs,
        String error
) {
    public static AudioFragmentResult success(Path audioPath) {
        return new AudioFragmentResult(
                true,
                audioPath,
                "ogg",
                OggDurationReader.durationMs(audioPath),
                null
        );
    }

    public static AudioFragmentResult failure(String error) {
        return new AudioFragmentResult(
                false,
                null,
                "ogg",
                0L,
                error == null ? "Unknown audio fragment error" : error
        );
    }
}