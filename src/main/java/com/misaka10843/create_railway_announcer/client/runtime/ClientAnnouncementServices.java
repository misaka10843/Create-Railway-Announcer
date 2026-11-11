package com.misaka10843.create_railway_announcer.client.runtime;

import com.misaka10843.create_railway_announcer.audio.AudioCache;
import com.misaka10843.create_railway_announcer.client.audio.LocalOggSequenceAudioBackend;
import com.misaka10843.create_railway_announcer.sequence.SequenceResolver;
import com.misaka10843.create_railway_announcer.tts.HttpTtsProvider;

public final class ClientAnnouncementServices {
    private static volatile Services services;
    private static final java.util.concurrent.atomic.AtomicLong PRELOAD = new java.util.concurrent.atomic.AtomicLong();
    private static volatile int remainingPreloads;
    public static int remainingPreloads() { return remainingPreloads; }

    private static final class Services {
        private final AudioCache cache = new AudioCache(new HttpTtsProvider());
        private final SequenceResolver resolver = new SequenceResolver(cache);
        private final LocalOggSequenceAudioBackend backend = new LocalOggSequenceAudioBackend();
        private final ClientAnnouncementRuntime runtime = new ClientAnnouncementRuntime(resolver, backend);
    }

    private ClientAnnouncementServices() {
    }

    public static ClientAnnouncementRuntime runtime() {
        return services().runtime;
    }

    public static LocalOggSequenceAudioBackend localBackend() {
        return services().backend;
    }

    public static SequenceResolver sequenceResolver() {
        return services().resolver;
    }

    public static AudioCache audioCache() {
        return services().cache;
    }

    public static boolean isInitialized() { return services != null; }

    public static void disconnect() {
        PRELOAD.incrementAndGet();
        remainingPreloads = 0;
        ClientAnnouncementRuntime.disconnect();
        Services current = services;
        if (current != null) current.backend.stop();
    }

    public static void preloadIfEnabled() {
        if (com.misaka10843.create_railway_announcer.config.ClientConfig.PRELOAD_ENABLED.get()
                && com.misaka10843.create_railway_announcer.config.ClientConfig.ANNOUNCEMENTS_ENABLED.get()
                && com.misaka10843.create_railway_announcer.config.AnnouncementClientData.enabled()) preload();
    }

    public record PreloadReport(int stations, int failures, boolean cancelled) {}
    public static java.util.concurrent.CompletableFuture<PreloadReport> preload() {
        long generation = PRELOAD.incrementAndGet();
        var failures = new java.util.concurrent.atomic.AtomicInteger();
        var stations = java.util.List.copyOf(com.misaka10843.create_railway_announcer.config.StationLineConfigStore.stations().values());
        remainingPreloads = stations.size();
        java.util.concurrent.CompletableFuture<Void> chain = java.util.concurrent.CompletableFuture.completedFuture(null);
        for (var station : stations) {
            chain = chain.thenCompose(ignored -> {
                if (generation != PRELOAD.get()) return java.util.concurrent.CompletableFuture.completedFuture(null);
                if (!station.isEnabled()) { remainingPreloads--; return java.util.concurrent.CompletableFuture.completedFuture(null); }
                var context = com.misaka10843.create_railway_announcer.sequence.AnnouncementResolveContext.forStationId(station.getCustomId());
                var futures = com.misaka10843.create_railway_announcer.announcement.AnnouncementSequenceMapper.manualEvents().stream()
                        .filter(event->com.misaka10843.create_railway_announcer.announcement.AnnouncementSequenceMapper.channel(event)!=com.misaka10843.create_railway_announcer.audio.AudioChannel.MELODY)
                        .map(com.misaka10843.create_railway_announcer.announcement.AnnouncementSequenceMapper::sequenceId)
                        .map(id -> com.misaka10843.create_railway_announcer.pack.VoicePackManager.sequences().get(id).orElse(null))
                        .filter(java.util.Objects::nonNull).map(template -> sequenceResolver().resolveAudioFragments(template, context)).toList();
                return java.util.concurrent.CompletableFuture.allOf(futures.toArray(java.util.concurrent.CompletableFuture[]::new))
                        .whenComplete((result, error) -> {
                            if (error == null) futures.forEach(future -> future.join().forEach(fragment -> { if (!fragment.ok()) failures.incrementAndGet(); }));
                            if (generation == PRELOAD.get()) remainingPreloads--;
                        });
            });
        }
        return chain.handle((ignored,error) -> new PreloadReport(stations.size(),failures.get() + (error == null ? 0 : 1),generation != PRELOAD.get()));
    }

    private static Services services() {
        Services current = services;
        if (current != null) return current;
        synchronized (ClientAnnouncementServices.class) {
            if (services == null) services = new Services();
            return services;
        }
    }
}
