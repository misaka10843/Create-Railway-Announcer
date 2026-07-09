package com.misaka10843.createrailwayannouncer.playback;

import com.misaka10843.createrailwayannouncer.CreateRailwayAnnouncer;
import com.misaka10843.createrailwayannouncer.audio.AudioChannel;
import com.misaka10843.createrailwayannouncer.sequence.ResolvedSequence;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class PlaybackManager {
    private static final long SAME_CHANNEL_REPLACE_DELAY_MS = 250L;

    private final Map<AudioChannel, PlaybackSession> sessions =
            Collections.synchronizedMap(new EnumMap<>(AudioChannel.class));

    public PlaybackStartResult play(ResolvedSequence sequence, SequenceAudioBackend backend) {
        if (sequence == null || backend == null) {
            return new PlaybackStartResult(
                    PlaybackStartDecision.REJECTED_LOWER_PRIORITY,
                    null,
                    null,
                    "Playback request rejected because sequence or backend is null."
            );
        }

        AudioChannel channel = sequence.channel();

        PlaybackSession previous;
        boolean replacing;

        synchronized (sessions) {
            previous = sessions.get(channel);

            if (previous != null) {
                int oldPriority = previous.sequence().priority();
                int newPriority = sequence.priority();

                if (newPriority < oldPriority) {
                    CreateRailwayAnnouncer.LOGGER.info(
                            "Rejected playback sequence {} on channel {} because active sequence {} has higher priority: new={}, old={}",
                            sequence.id(),
                            channel,
                            previous.sequence().id(),
                            newPriority,
                            oldPriority
                    );

                    return PlaybackStartResult.rejectedLowerPriority(previous);
                }

                CreateRailwayAnnouncer.LOGGER.info(
                        "Replacing playback session {} on channel {}: oldSequence={}, oldPriority={}, newSequence={}, newPriority={}",
                        previous.id(),
                        channel,
                        previous.sequence().id(),
                        oldPriority,
                        sequence.id(),
                        newPriority
                );

                previous.stop();
            }

            replacing = previous != null;

            PlaybackScheduler scheduler = new PlaybackScheduler();
            PlaybackSession session = new PlaybackSession(channel, sequence, scheduler);

            sessions.put(channel, session);

            CompletableFuture<PlaybackState> playFuture = CompletableFuture
                    .supplyAsync(() -> {
                        if (replacing) {
                            try {
                                Thread.sleep(SAME_CHANNEL_REPLACE_DELAY_MS);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                        }

                        return scheduler.play(sequence, backend).join();
                    })
                    .whenComplete((state, throwable) -> {
                        synchronized (sessions) {
                            PlaybackSession current = sessions.get(channel);

                            if (current == session) {
                                sessions.remove(channel);
                            }
                        }

                        if (throwable != null) {
                            CreateRailwayAnnouncer.LOGGER.error(
                                    "Playback session {} failed on channel {}",
                                    session.id(),
                                    channel,
                                    throwable
                            );
                            return;
                        }

                        CreateRailwayAnnouncer.LOGGER.info(
                                "Playback session {} ended with state {} on channel {}",
                                session.id(),
                                state,
                                channel
                        );
                    });

            session.setFuture(playFuture);

            if (previous != null) {
                return PlaybackStartResult.replaced(session, previous);
            }

            return PlaybackStartResult.started(session);
        }
    }

    public void stopAll() {
        synchronized (sessions) {
            for (PlaybackSession session : sessions.values()) {
                session.stop();
            }

            sessions.clear();
        }
    }

    public Optional<PlaybackSession> get(AudioChannel channel) {
        synchronized (sessions) {
            return Optional.ofNullable(sessions.get(channel));
        }
    }

    public Map<AudioChannel, PlaybackSession> sessions() {
        synchronized (sessions) {
            return Map.copyOf(sessions);
        }
    }
}