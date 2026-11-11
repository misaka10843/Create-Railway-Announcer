package com.misaka10843.create_railway_announcer.playback;

import com.misaka10843.create_railway_announcer.CreateRailwayAnnouncer;
import com.misaka10843.create_railway_announcer.sequence.ResolvedSequence;
import com.misaka10843.create_railway_announcer.sequence.ResolvedSequenceItem;

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
    private volatile Thread runningThread;

    private static void pause(int pauseMs) throws InterruptedException {
        if (pauseMs <= 0) {
            return;
        }

        PlaybackClock.GAME.sleep(pauseMs);
    }

    public PlaybackState state() {
        return state;
    }

    public CompletableFuture<PlaybackState> play(ResolvedSequence sequence, SequenceAudioBackend backend) {
        return CompletableFuture.supplyAsync(() -> {
            runningThread = Thread.currentThread();
            try { return run(sequence, backend); }
            finally {
                runningThread = null;
                executor.shutdown();
            }
        }, executor);
    }

    public CompletableFuture<PlaybackState> playDry(ResolvedSequence sequence, SequenceAudioBackend backend) {
        return play(sequence, backend);
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
        Thread active = runningThread;
        if (active != null) active.interrupt();
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

        if (stopRequested) {
            state = PlaybackState.STOPPED;
            return state;
        }
        state = PlaybackState.PLAYING;
        currentBackend = backend;

        try {
            backend.onSequenceStart(sequence);

            for (ResolvedSequenceItem item : sequence.items()) {
                PlaybackClock.GAME.awaitPlaying();
                if (stopRequested) {
                    state = PlaybackState.STOPPED;
                    backend.onSequenceEnd(sequence, state);
                    return state;
                }

                switch (item.type()) {
                    case AUDIO -> backend.playAudio(sequence, item).get();
                    case SOUND -> backend.playSound(sequence, item).get();
                    case SUBTITLE -> backend.showSubtitle(sequence, item);
                    case PAUSE -> {
                        CreateRailwayAnnouncer.LOGGER.info(
                                "[{}] PAUSE {} ms",
                                sequence.id(),
                                item.pauseMs()
                        );
                        pause(item.pauseMs());
                    }
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
}
