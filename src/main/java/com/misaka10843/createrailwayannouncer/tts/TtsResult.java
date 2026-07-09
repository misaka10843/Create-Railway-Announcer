package com.misaka10843.createrailwayannouncer.tts;

import java.nio.file.Path;

public record TtsResult(
        boolean ok,
        Path output,
        String backend,
        String voiceId,
        String error
) {
    public static TtsResult success(Path output, String backend, String voiceId) {
        return new TtsResult(true, output, backend, voiceId, null);
    }

    public static TtsResult failure(String error) {
        return new TtsResult(false, null, null, null, error == null ? "Unknown TTS error" : error);
    }
}