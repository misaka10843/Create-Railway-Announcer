package com.misaka10843.create_railway_announcer.audio;

import com.misaka10843.create_railway_announcer.tts.TtsBackend;

public record AudioFragmentKey(
        AudioFragmentType type,
        String id,
        String variant,
        TtsBackend backend,
        String language,
        String voiceContains,
        double rate,
        int volume,
        String input,
        boolean ssml,
        String pitch
) {
    public AudioFragmentKey(AudioFragmentType type, String id, String variant, TtsBackend backend, String language,
                            String voice, double rate, int volume, String input, boolean ssml) {
        this(type, id, variant, backend, language, voice, rate, volume, input, ssml, "");
    }
}
