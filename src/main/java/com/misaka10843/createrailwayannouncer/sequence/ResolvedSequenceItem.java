package com.misaka10843.createrailwayannouncer.sequence;

import java.nio.file.Path;

public record ResolvedSequenceItem(
        ResolvedSequenceItemType type,
        Path audioPath,
        int pauseMs,
        String text
) {
    public static ResolvedSequenceItem audio(Path audioPath) {
        return new ResolvedSequenceItem(ResolvedSequenceItemType.AUDIO, audioPath, 0, null);
    }

    public static ResolvedSequenceItem pause(int pauseMs) {
        return new ResolvedSequenceItem(ResolvedSequenceItemType.PAUSE, null, Math.max(0, pauseMs), null);
    }

    public static ResolvedSequenceItem sound(Path audioPath) {
        return new ResolvedSequenceItem(ResolvedSequenceItemType.SOUND, audioPath, 0, null);
    }

    public static ResolvedSequenceItem subtitle(String text) {
        return new ResolvedSequenceItem(ResolvedSequenceItemType.SUBTITLE, null, 0, text);
    }
}