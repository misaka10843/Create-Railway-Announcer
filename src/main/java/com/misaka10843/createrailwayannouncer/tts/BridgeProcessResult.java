package com.misaka10843.createrailwayannouncer.tts;

public record BridgeProcessResult(
        int exitCode,
        String stdout,
        String stderr,
        boolean timedOut
) {
    public boolean success() {
        return !timedOut && exitCode == 0;
    }
}