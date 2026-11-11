package com.misaka10843.create_railway_announcer.audio;

import java.nio.file.Path;

public interface AudioEncoder {
    String id();

    AudioEncodeResult encodeToOgg(Path wavInput, Path oggOutput);
}