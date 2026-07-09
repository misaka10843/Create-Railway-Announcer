package com.misaka10843.createrailwayannouncer.client.runtime;

import com.misaka10843.createrailwayannouncer.CreateRailwayAnnouncer;
import com.misaka10843.createrailwayannouncer.pack.VoicePackManager;
import com.misaka10843.createrailwayannouncer.playback.PlaybackManager;
import com.misaka10843.createrailwayannouncer.playback.PlaybackSession;
import com.misaka10843.createrailwayannouncer.playback.PlaybackStartResult;
import com.misaka10843.createrailwayannouncer.playback.SequenceAudioBackend;
import com.misaka10843.createrailwayannouncer.sequence.AnnouncementResolveContext;
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

        CreateRailwayAnnouncer.LOGGER.info(
                "Client announcement runtime resolving request: event={}, sequence={}, station={}, train={}",
                request.eventType(),
                request.sequenceId(),
                request.stationId(),
                request.trainId()
        );

        AnnouncementResolveContext resolveContext =
                AnnouncementResolveContext.forStationId(request.stationId());

        return sequenceResolver.resolveSequence(template, resolveContext)
                .thenApply(sequence -> {
                    PlaybackStartResult result = PLAYBACK_MANAGER.play(sequence, backend);

                    if (result.accepted()) {
                        PlaybackSession session = result.session();
                        CreateRailwayAnnouncer.LOGGER.info(
                                "Client announcement playback accepted: sequence={}, channel={}, priority={}, decision={}",
                                session.sequence().id(),
                                session.channel(),
                                session.sequence().priority(),
                                result.decision()
                        );
                    } else {
                        CreateRailwayAnnouncer.LOGGER.info(
                                "Client announcement playback rejected: sequence={}, reason={}",
                                request.sequenceId(),
                                result.message()
                        );
                    }

                    return result;
                });
    }
}