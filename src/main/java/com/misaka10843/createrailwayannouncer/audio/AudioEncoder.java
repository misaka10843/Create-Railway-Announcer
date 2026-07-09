package com.misaka10843.createrailwayannouncer.audio;

import java.nio.file.Path;

public interface AudioEncoder {
    String id();

    AudioEncodeResult encodeWavToOgg(Path wavInput, Path oggOutput);
}