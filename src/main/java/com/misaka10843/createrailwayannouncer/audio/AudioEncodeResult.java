package com.misaka10843.createrailwayannouncer.audio;

import java.nio.file.Path;

public record AudioEncodeResult(
        boolean ok,
        Path output,
        String error
) {
    public static AudioEncodeResult success(Path output) {
        return new AudioEncodeResult(true, output, null);
    }

    public static AudioEncodeResult failure(String error) {
        return new AudioEncodeResult(false, null, error == null ? "Unknown audio encode error" : error);
    }
}