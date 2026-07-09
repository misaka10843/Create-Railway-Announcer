package com.misaka10843.createrailwayannouncer.pack;

import java.nio.file.Path;
import java.util.List;

public record VoicePack(
        int packFormat,
        String id,
        String name,
        String description,
        String author,
        String version,
        String defaultLanguage,
        List<String> supportedLanguages,
        Path root
) {
}