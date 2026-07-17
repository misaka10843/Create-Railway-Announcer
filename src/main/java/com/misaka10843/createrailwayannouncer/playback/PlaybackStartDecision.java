package com.misaka10843.createrailwayannouncer.playback;

public enum PlaybackStartDecision {
    STARTED,
    REPLACED,
    REJECTED_LOWER_PRIORITY,
    SKIPPED_EXPIRED
}