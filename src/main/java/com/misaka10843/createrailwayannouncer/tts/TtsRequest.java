package com.misaka10843.createrailwayannouncer.tts;

import java.nio.file.Path;

public record TtsRequest(
        TtsBackend backend,
        String language,
        String voiceContains,
        double rate,
        int volume,
        String input,
        boolean ssml,
        Path output
) {
}
