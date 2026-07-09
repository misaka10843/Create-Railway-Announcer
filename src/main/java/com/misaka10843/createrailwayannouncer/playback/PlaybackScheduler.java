package com.misaka10843.createrailwayannouncer.playback;

import com.misaka10843.createrailwayannouncer.CreateRailwayAnnouncer;
import com.misaka10843.createrailwayannouncer.sequence.ResolvedSequence;
import com.misaka10843.createrailwayannouncer.sequence.ResolvedSequenceItem;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class PlaybackScheduler {
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "Create Railway Announcer Playback Scheduler");
        thread.setDaemon(true);
        return thread;
    });

    private volatile PlaybackState state = PlaybackState.IDLE;
    private volatile boolean stopRequested = false;
    private volatile SequenceAudioBackend currentBackend;

    public PlaybackState state() {
        return state;
    }

    public CompletableFuture<PlaybackState> play(ResolvedSequence sequence, SequenceAudioBackend backend) {
        return CompletableFuture.supplyAsync(() -> run(sequence, backend), executor);
    }

    public CompletableFuture<PlaybackState> playDry(ResolvedSequence sequence, SequenceAudioBackend backend) {
        return CompletableFuture.supplyAsync(() -> run(sequence, backend), executor);
    }

    public void stop() {
        stopRequested = true;

        SequenceAudioBackend backend = currentBackend;
        if (backend != null) {
            try {
                backend.fadeOutAndStop(400);
            } catch (Throwable t) {
                CreateRailwayAnnouncer.LOGGER.warn("Failed to stop playback backend", t);
            }
        }
    }

    private PlaybackState run(ResolvedSequence sequence, SequenceAudioBackend backend) {
        if (sequence == null) {
            state = PlaybackState.FAILED;
            return state;
        }

        if (backend == null) {
            state = PlaybackState.FAILED;
            return state;
        }

        stopRequested = false;
        state = PlaybackState.PLAYING;
        currentBackend = backend;

        try {
            backend.onSequenceStart(sequence);

            for (ResolvedSequenceItem item : sequence.items()) {
                if (stopRequested) {
                    state = PlaybackState.STOPPED;
                    backend.onSequenceEnd(sequence, state);
                    return state;
                }

                switch (item.type()) {
                    case AUDIO -> backend.playAudio(sequence, item).join();
                    case SOUND -> backend.playSound(sequence, item).join();
                    case SUBTITLE -> backend.showSubtitle(sequence, item);
                    case PAUSE -> pause(item.pauseMs());
                }
            }

            state = PlaybackState.FINISHED;
            backend.onSequenceEnd(sequence, state);
            return state;
        } catch (Throwable t) {
            if (stopRequested) {
                state = PlaybackState.STOPPED;
            } else {
                state = PlaybackState.FAILED;
                CreateRailwayAnnouncer.LOGGER.error("Playback scheduler failed", t);
            }

            try {
                backend.onSequenceEnd(sequence, state);
            } catch (Throwable ignored) {
            }

            return state;
        } finally {
            currentBackend = null;
        }
    }

    private static void pause(int pauseMs) throws InterruptedException {
        if (pauseMs <= 0) {
            return;
        }

        Thread.sleep(pauseMs);
    }
}