package com.misaka10843.createrailwayannouncer.client.runtime;

import com.misaka10843.createrailwayannouncer.CreateRailwayAnnouncer;
import com.misaka10843.createrailwayannouncer.pack.VoicePackManager;
import com.misaka10843.createrailwayannouncer.playback.PlaybackManager;
import com.misaka10843.createrailwayannouncer.playback.PlaybackSession;
import com.misaka10843.createrailwayannouncer.playback.PlaybackStartResult;
import com.misaka10843.createrailwayannouncer.playback.SequenceAudioBackend;
import com.misaka10843.createrailwayannouncer.sequence.AnnouncementResolveContext;
import com.misaka10843.createrailwayannouncer.sequence.ResolvedSequence;
import com.misaka10843.createrailwayannouncer.sequence.SequenceResolver;
import com.misaka10843.createrailwayannouncer.sequence.SequenceTemplate;

import java.util.concurrent.CompletableFuture;

public final class ClientAnnouncementRuntime {
    private static final PlaybackManager PLAYBACK_MANAGER = new PlaybackManager();

    private final SequenceResolver sequenceResolver;
    private final SequenceAudioBackend backend;

    public ClientAnnouncementRuntime(
            SequenceResolver sequenceResolver,
            SequenceAudioBackend backend
    ) {
        this.sequenceResolver = sequenceResolver;
        this.backend = backend;
    }

    public static PlaybackManager playbackManager() {
        return PLAYBACK_MANAGER;
    }

    public CompletableFuture<PlaybackStartResult> play(AnnouncementPlaybackRequest request) {
        return play(request, 0L);
    }

    public CompletableFuture<PlaybackStartResult> play(
            AnnouncementPlaybackRequest request,
            long offsetMs
    ) {
        if (request == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("request is null"));
        }

        SequenceTemplate template = VoicePackManager.sequences()
                .get(request.sequenceId())
                .orElse(null);

        if (template == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException(
                    "Sequence not found: " + request.sequenceId()
            ));
        }

        long safeOffsetMs = Math.max(0L, offsetMs);

        CreateRailwayAnnouncer.LOGGER.info(
                "Client announcement runtime resolving request: event={}, sequence={}, station={}, train={}, offsetMs={}",
                request.eventType(),
                request.sequenceId(),
                request.stationId(),
                request.trainId(),
                safeOffsetMs
        );

        AnnouncementResolveContext resolveContext =
                AnnouncementResolveContext.forStationId(request.stationId());

        return sequenceResolver.resolveSequence(template, resolveContext)
                .thenApply(sequence -> {
                    ResolvedSequence playbackSequence = sequence;

                    if (safeOffsetMs > 0L) {
                        playbackSequence = sequence.skipTo(safeOffsetMs);
                    }

                    if (playbackSequence.isEmpty()) {
                        CreateRailwayAnnouncer.LOGGER.info(
                                "Client announcement playback skipped: sequence={}, offsetMs={}, originalTotalDurationMs={}",
                                sequence.id(),
                                safeOffsetMs,
                                sequence.totalDurationMs()
                        );

                        return PlaybackStartResult.skippedExpired();
                    }

                    PlaybackStartResult result = PLAYBACK_MANAGER.play(playbackSequence, backend);

                    if (result.accepted()) {
                        PlaybackSession session = result.session();
                        CreateRailwayAnnouncer.LOGGER.info(
                                "Client announcement playback accepted: sequence={}, channel={}, priority={}, decision={}, offsetMs={}, totalDurationMs={}",
                                session.sequence().id(),
                                session.channel(),
                                session.sequence().priority(),
                                result.decision(),
                                safeOffsetMs,
                                session.sequence().totalDurationMs()
                        );
                    } else {
                        CreateRailwayAnnouncer.LOGGER.info(
                                "Client announcement playback rejected/skipped: sequence={}, decision={}, reason={}",
                                request.sequenceId(),
                                result.decision(),
                                result.message()
                        );
                    }

                    return result;
                });
    }
}