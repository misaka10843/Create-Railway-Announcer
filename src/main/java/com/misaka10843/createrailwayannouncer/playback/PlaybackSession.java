package com.misaka10843.createrailwayannouncer.playback;

import com.misaka10843.createrailwayannouncer.audio.AudioChannel;
import com.misaka10843.createrailwayannouncer.sequence.ResolvedSequence;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class PlaybackSession {
    private final UUID id;
    private final AudioChannel channel;
    private final ResolvedSequence sequence;
    private final PlaybackScheduler scheduler;
    private final Instant startedAt;
    private CompletableFuture<PlaybackState> future;

    public PlaybackSession(
            AudioChannel channel,
            ResolvedSequence sequence,
            PlaybackScheduler scheduler
    ) {
        this.id = UUID.randomUUID();
        this.channel = channel;
        this.sequence = sequence;
        this.scheduler = scheduler;
        this.startedAt = Instant.now();
    }

    public UUID id() {
        return id;
    }

    public AudioChannel channel() {
        return channel;
    }

    public ResolvedSequence sequence() {
        return sequence;
    }

    public PlaybackScheduler scheduler() {
        return scheduler;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public CompletableFuture<PlaybackState> future() {
        return future;
    }

    public void setFuture(CompletableFuture<PlaybackState> future) {
        this.future = future;
    }

    public PlaybackState state() {
        return scheduler.state();
    }

    public void stop() {
        scheduler.stop();
    }
}