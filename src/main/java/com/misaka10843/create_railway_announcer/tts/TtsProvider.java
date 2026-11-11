package com.misaka10843.create_railway_announcer.tts;

import java.util.concurrent.CompletableFuture;

public interface TtsProvider {
    String id();

    boolean isAvailable();

    default TtsProvider snapshot() { return this; }

    CompletableFuture<TtsResult> synthesize(TtsRequest request);
}
