package com.misaka10843.create_railway_announcer.client.runtime;

import com.misaka10843.create_railway_announcer.CreateRailwayAnnouncer;
import com.misaka10843.create_railway_announcer.pack.VoicePackManager;
import com.misaka10843.create_railway_announcer.playback.PlaybackManager;
import com.misaka10843.create_railway_announcer.playback.PlaybackSession;
import com.misaka10843.create_railway_announcer.playback.PlaybackStartResult;
import com.misaka10843.create_railway_announcer.playback.SequenceAudioBackend;
import com.misaka10843.create_railway_announcer.sequence.AnnouncementResolveContext;
import com.misaka10843.create_railway_announcer.sequence.ResolvedSequence;
import com.misaka10843.create_railway_announcer.sequence.SequenceResolver;
import com.misaka10843.create_railway_announcer.sequence.SequenceTemplate;

import java.util.concurrent.CompletableFuture;

public final class ClientAnnouncementRuntime {
    private static final PlaybackManager PLAYBACK_MANAGER = new PlaybackManager();
    private static final java.util.concurrent.atomic.AtomicLong CONNECTION = new java.util.concurrent.atomic.AtomicLong();
    private static final java.util.concurrent.ConcurrentMap<java.util.UUID,String> STATIONS = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.ConcurrentMap<java.util.UUID,com.misaka10843.create_railway_announcer.client.AnnouncementAudience> AUDIENCES = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.ConcurrentMap<String,Long> STOPS=new java.util.concurrent.ConcurrentHashMap<>();
    public static void stopStation(String station,com.misaka10843.create_railway_announcer.audio.AudioChannel channel){
        STOPS.merge(channel==null?station:station+"|"+channel,1L,Long::sum);
        for(var session:PLAYBACK_MANAGER.sessions().values())if(station.equals(STATIONS.get(session.id()))&&(channel==null||session.channel()==channel))session.stop();
    }
    private static boolean accepts(com.misaka10843.create_railway_announcer.client.AnnouncementAudience audience) {
        var player = net.minecraft.client.Minecraft.getInstance().player;
        return player != null && audience.accepts(player.blockPosition(),
                com.misaka10843.create_railway_announcer.compat.create.CreateTrainAdapterHolder.get().ridingTrainId(player).orElse(null));
    }
    private static boolean relativeAudio(com.misaka10843.create_railway_announcer.client.AnnouncementAudience audience) {
        if (audience.channel() == com.misaka10843.create_railway_announcer.audio.AudioChannel.ONBOARD_VOICE) return true;
        var player = net.minecraft.client.Minecraft.getInstance().player;
        return audience.channel() == com.misaka10843.create_railway_announcer.audio.AudioChannel.DOOR_CHIME && player != null
                && com.misaka10843.create_railway_announcer.compat.create.CreateTrainAdapterHolder.get().ridingTrainId(player)
                .filter(id -> id.equals(audience.trainId())).isPresent();
    }
    public static void stopInvalidAudience() {
        for (var session : PLAYBACK_MANAGER.sessions().values()) {
            var audience = AUDIENCES.get(session.id());
            if (audience != null && !accepts(audience)) session.stop();
        }
    }
    public static void stopDisabledStations() {
        for (var session : PLAYBACK_MANAGER.sessions().values()) {
            String id = STATIONS.get(session.id());
            if (id != null && !id.isBlank() && !com.misaka10843.create_railway_announcer.config.StationLineConfigStore.station(id).map(value -> value.isEnabled()).orElse(false)) session.stop();
        }
    }

    public static void disconnect() {
        CONNECTION.incrementAndGet();
        PLAYBACK_MANAGER.stopAll();
        STATIONS.clear();
        AUDIENCES.clear();
        STOPS.clear();
    }

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
        if(template.channel()!=com.misaka10843.create_railway_announcer.announcement.AnnouncementSequenceMapper.channel(request.eventType()))
            return CompletableFuture.failedFuture(new IllegalArgumentException("Voice pack template uses the wrong playback channel"));

        long safeOffsetMs = Math.max(0L, offsetMs);
        long connection = CONNECTION.get();
        long stationStop=STOPS.getOrDefault(request.stationId(),0L),channelStop=STOPS.getOrDefault(request.stationId()+"|"+template.channel(),0L);

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
        if (resolveContext.hasStation() && !resolveContext.station().isEnabled()) return CompletableFuture.completedFuture(PlaybackStartResult.skippedExpired());

        return sequenceResolver.resolveSequence(template, resolveContext)
                .thenApply(sequence -> {
                    if (CONNECTION.get() != connection) return PlaybackStartResult.skippedExpired();
                    if(stationStop!=STOPS.getOrDefault(request.stationId(),0L)||channelStop!=STOPS.getOrDefault(request.stationId()+"|"+template.channel(),0L))return PlaybackStartResult.skippedExpired();
                    if (resolveContext.hasStation() && !com.misaka10843.create_railway_announcer.config.StationLineConfigStore.station(request.stationId()).map(value -> value.isEnabled()).orElse(false)) return PlaybackStartResult.skippedExpired();
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

                    var audience = com.misaka10843.create_railway_announcer.client.AnnouncementAudience.forRequest(request,playbackSequence.channel());
                    if (backend instanceof com.misaka10843.create_railway_announcer.client.audio.LocalOggSequenceAudioBackend && !accepts(audience)) return PlaybackStartResult.skippedExpired();
                    SequenceAudioBackend scoped = backend instanceof com.misaka10843.create_railway_announcer.client.audio.LocalOggSequenceAudioBackend local
                            ? local.scoped(request.position(),request.horizontalRange(),relativeAudio(audience))
                            : backend;
                    PlaybackStartResult result = PLAYBACK_MANAGER.play(playbackSequence, scoped);

                    if (result.accepted()) {
                        PlaybackSession session = result.session();
                        STATIONS.put(session.id(),request.stationId());
                        AUDIENCES.put(session.id(),audience);
                        session.future().whenComplete((state,error) -> {STATIONS.remove(session.id());AUDIENCES.remove(session.id());});
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
