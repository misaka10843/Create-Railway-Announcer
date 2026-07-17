package com.misaka10843.createrailwayannouncer.sequence;

import com.misaka10843.createrailwayannouncer.audio.OggDurationReader;

import java.nio.file.Path;

public record ResolvedSequenceItem(
        ResolvedSequenceItemType type,
        Path audioPath,
        int pauseMs,
        String text,
        long durationMs,
        long startOffsetMs
) {
    public static ResolvedSequenceItem audio(Path audioPath) {
        return audio(audioPath, OggDurationReader.durationMs(audioPath));
    }

    public static ResolvedSequenceItem audio(Path audioPath, long durationMs) {
        return new ResolvedSequenceItem(
                ResolvedSequenceItemType.AUDIO,
                audioPath,
                0,
                null,
                Math.max(0L, durationMs),
                0L
        );
    }

    public static ResolvedSequenceItem pause(int pauseMs) {
        int safePauseMs = Math.max(0, pauseMs);

        return new ResolvedSequenceItem(
                ResolvedSequenceItemType.PAUSE,
                null,
                safePauseMs,
                null,
                safePauseMs,
                0L
        );
    }

    public static ResolvedSequenceItem sound(Path audioPath) {
        return sound(audioPath, OggDurationReader.durationMs(audioPath));
    }

    public static ResolvedSequenceItem sound(Path audioPath, long durationMs) {
        return new ResolvedSequenceItem(
                ResolvedSequenceItemType.SOUND,
                audioPath,
                0,
                null,
                Math.max(0L, durationMs),
                0L
        );
    }

    public static ResolvedSequenceItem subtitle(String text) {
        return new ResolvedSequenceItem(
                ResolvedSequenceItemType.SUBTITLE,
                null,
                0,
                text,
                0L,
                0L
        );
    }

    public ResolvedSequenceItem withStartOffset(long offsetMs) {
        long safeOffsetMs = Math.max(0L, offsetMs);

        if (type != ResolvedSequenceItemType.AUDIO && type != ResolvedSequenceItemType.SOUND) {
            return this;
        }

        long remainingDurationMs = Math.max(0L, durationMs - safeOffsetMs);

        return new ResolvedSequenceItem(
                type,
                audioPath,
                pauseMs,
                text,
                remainingDurationMs,
                safeOffsetMs
        );
    }

    public ResolvedSequenceItem withRemainingPause(long remainingMs) {
        if (type != ResolvedSequenceItemType.PAUSE) {
            return this;
        }

        int safeRemainingMs = (int) Math.max(0L, Math.min(Integer.MAX_VALUE, remainingMs));
        return pause(safeRemainingMs);
    }
}