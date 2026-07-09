package com.misaka10843.createrailwayannouncer.playback;

import com.misaka10843.createrailwayannouncer.sequence.ResolvedSequence;
import com.misaka10843.createrailwayannouncer.sequence.ResolvedSequenceItem;

import java.util.concurrent.CompletableFuture;

public interface SequenceAudioBackend {
    String id();

    void onSequenceStart(ResolvedSequence sequence);

    void onSequenceEnd(ResolvedSequence sequence, PlaybackState state);

    CompletableFuture<Void> playAudio(ResolvedSequence sequence, ResolvedSequenceItem item);

    CompletableFuture<Void> playSound(ResolvedSequence sequence, ResolvedSequenceItem item);

    void showSubtitle(ResolvedSequence sequence, ResolvedSequenceItem item);

    default void stop() {
        fadeOutAndStop(0);
    }

    default void fadeOutAndStop(int fadeOutMs) {
    }
}