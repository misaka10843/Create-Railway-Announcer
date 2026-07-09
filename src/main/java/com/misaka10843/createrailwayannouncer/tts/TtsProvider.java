package com.misaka10843.createrailwayannouncer.tts;

import java.util.concurrent.CompletableFuture;

public interface TtsProvider {
    String id();

    boolean isAvailable();

    CompletableFuture<TtsResult> synthesize(TtsRequest request);
}