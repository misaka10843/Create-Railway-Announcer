package com.misaka10843.createrailwayannouncer.client.runtime;

import com.misaka10843.createrailwayannouncer.audio.AudioCache;
import com.misaka10843.createrailwayannouncer.client.audio.LocalOggSequenceAudioBackend;
import com.misaka10843.createrailwayannouncer.sequence.SequenceResolver;
import com.misaka10843.createrailwayannouncer.tts.WindowsBridgeTtsProvider;

public final class ClientAnnouncementServices {
    private static final WindowsBridgeTtsProvider WINDOWS_TTS = new WindowsBridgeTtsProvider();
    private static final AudioCache AUDIO_CACHE = new AudioCache(WINDOWS_TTS);
    private static final SequenceResolver SEQUENCE_RESOLVER = new SequenceResolver(AUDIO_CACHE);
    private static final LocalOggSequenceAudioBackend LOCAL_BACKEND = new LocalOggSequenceAudioBackend();

    private static final ClientAnnouncementRuntime RUNTIME =
            new ClientAnnouncementRuntime(SEQUENCE_RESOLVER, LOCAL_BACKEND);

    private ClientAnnouncementServices() {
    }

    public static ClientAnnouncementRuntime runtime() {
        return RUNTIME;
    }

    public static LocalOggSequenceAudioBackend localBackend() {
        return LOCAL_BACKEND;
    }

    public static SequenceResolver sequenceResolver() {
        return SEQUENCE_RESOLVER;
    }

    public static AudioCache audioCache() {
        return AUDIO_CACHE;
    }
}