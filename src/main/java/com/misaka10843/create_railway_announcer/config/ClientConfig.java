package com.misaka10843.create_railway_announcer.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class ClientConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec.BooleanValue ANNOUNCEMENTS_ENABLED = BUILDER.define("audio.enabled", true);
    public static final ModConfigSpec.BooleanValue DOOR_CHIMES_ENABLED = BUILDER.define("audio.doorChimes", true);
    public static final ModConfigSpec.BooleanValue ONBOARD_ENABLED = BUILDER.define("audio.onboardEnabled", true);
    public static final ModConfigSpec.BooleanValue PLATFORM_ENABLED = BUILDER.define("audio.platformEnabled", true);
    public static final ModConfigSpec.ConfigValue<String> VOICE_PACK = BUILDER.define("voicePack.id","jr_like_default");
    public static final ModConfigSpec.BooleanValue PRELOAD_ENABLED = BUILDER.define("cache.preload", false);

    public static final ModConfigSpec.BooleanValue TTS_ENABLED = BUILDER
            .comment("Enable client-side text to speech generation.")
            .define("tts.enabled", true);

    public static final ModConfigSpec.ConfigValue<String> TTS_BACKEND = BUILDER
            .comment("TTS backend: http or off.")
            .define("tts.backend", "http");

    public static final ModConfigSpec.ConfigValue<String> TTS_LANGUAGE = BUILDER
            .comment("Preferred TTS language.")
            .define("tts.language", "ja-JP");

    public static final ModConfigSpec.ConfigValue<String> TTS_PREFERRED_VOICE_CONTAINS = BUILDER
            .comment("Exact neural voice id.")
            .define("tts.voice", "ja-JP-NanamiNeural");

    public static final ModConfigSpec.ConfigValue<String> TTS_ENDPOINT = BUILDER
            .define("tts.endpoint", "https://tts.wangwangit.com/v1/audio/speech");
    public static final ModConfigSpec.ConfigValue<String> TTS_TOKEN = BUILDER
            .define("tts.token", "");
    public static final ModConfigSpec.ConfigValue<String> TTS_PITCH = BUILDER
            .define("tts.pitch", "0Hz");
    public static final ModConfigSpec.IntValue TTS_TIMEOUT = BUILDER
            .defineInRange("tts.timeoutSeconds", 30, 1, 120);

    public static final ModConfigSpec.DoubleValue TTS_RATE = BUILDER
            .comment("TTS rate multiplier used in SSML.")
            .defineInRange("tts.rate", 0.85D, 0.5D, 2.0D);

    public static final ModConfigSpec.IntValue TTS_VOLUME = BUILDER
            .comment("TTS output volume used in SSML.")
            .defineInRange("tts.volume", 95, 0, 100);

    public static final ModConfigSpec.BooleanValue SUBTITLES_ENABLED = BUILDER
            .comment("Show announcement subtitles when playing audio, and use subtitles as fallback when TTS fails.")
            .define("audio.enableSubtitles", true);

    public static final ModConfigSpec.DoubleValue ONBOARD_VOLUME = BUILDER
            .comment("Onboard announcement volume multiplier.")
            .defineInRange("audio.onboardVolume", 1.0D, 0.0D, 2.0D);

    public static final ModConfigSpec.DoubleValue PLATFORM_VOLUME = BUILDER
            .comment("Platform announcement volume multiplier.")
            .defineInRange("audio.platformVolume", 1.0D, 0.0D, 2.0D);

    public static final ModConfigSpec.DoubleValue MASTER_VOLUME = BUILDER
            .comment("Master volume multiplier for Create Railway Announcer local playback.")
            .defineInRange("audio.masterVolume", 1.0D, 0.0D, 1.0D);
    public static final ModConfigSpec.DoubleValue MELODY_VOLUME = BUILDER
            .comment("Volume multiplier for platform melody playback.")
            .defineInRange("audio.melodyVolume", 0.8D, 0.0D, 1.0D);
    public static final ModConfigSpec.DoubleValue CHIME_VOLUME = BUILDER
            .comment("Volume multiplier for chime and short sound effects.")
            .defineInRange("audio.chimeVolume", 0.9D, 0.0D, 1.0D);

    public static final ModConfigSpec.DoubleValue PLATFORM_VOLUME_WHEN_ON_TRAIN = BUILDER
            .comment("Platform voice volume multiplier when the listener is on the related train.")
            .defineInRange("audio.platformVolumeWhenOnTrain", 0.25D, 0.0D, 1.0D);

    public static final ModConfigSpec.BooleanValue CACHE_ENABLED = BUILDER
            .comment("Cache generated TTS fragments.")
            .define("cache.enabled", true);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private ClientConfig() {
    }
}
