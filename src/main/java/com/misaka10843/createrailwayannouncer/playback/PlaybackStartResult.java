package com.misaka10843.createrailwayannouncer.playback;

public record PlaybackStartResult(
        PlaybackStartDecision decision,
        PlaybackSession session,
        PlaybackSession previous,
        String message
) {
    public static PlaybackStartResult started(PlaybackSession session) {
        return new PlaybackStartResult(
                PlaybackStartDecision.STARTED,
                session,
                null,
                "Playback session started."
        );
    }

    public static PlaybackStartResult replaced(PlaybackSession session, PlaybackSession previous) {
        return new PlaybackStartResult(
                PlaybackStartDecision.REPLACED,
                session,
                previous,
                "Playback session replaced previous session."
        );
    }

    public static PlaybackStartResult rejectedLowerPriority(PlaybackSession previous) {
        return new PlaybackStartResult(
                PlaybackStartDecision.REJECTED_LOWER_PRIORITY,
                null,
                previous,
                "Playback request rejected because an active session has higher priority."
        );
    }

    public static PlaybackStartResult skippedExpired() {
        return new PlaybackStartResult(
                PlaybackStartDecision.SKIPPED_EXPIRED,
                null,
                null,
                "Playback request skipped because catch-up offset exceeded sequence duration."
        );
    }

    public boolean accepted() {
        return decision == PlaybackStartDecision.STARTED
                || decision == PlaybackStartDecision.REPLACED;
    }
}