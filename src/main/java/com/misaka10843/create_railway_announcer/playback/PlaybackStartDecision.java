package com.misaka10843.create_railway_announcer.playback;

public enum PlaybackStartDecision {
    STARTED,
    REPLACED,
    REJECTED_LOWER_PRIORITY,
    SKIPPED_EXPIRED
}