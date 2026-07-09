package com.misaka10843.createrailwayannouncer.audio;

import java.nio.file.Path;

public record AudioFragmentResult(
        boolean ok,
        Path audioPath,
        String format,
        String error
) {
    public static AudioFragmentResult success(Path audioPath) {
        return new AudioFragmentResult(true, audioPath, "ogg", null);
    }

    public static AudioFragmentResult failure(String error) {
        return new AudioFragmentResult(false, null, "ogg", error == null ? "Unknown audio fragment error" : error);
    }
}