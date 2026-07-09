package com.misaka10843.createrailwayannouncer.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class ClientConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue TTS_ENABLED = BUILDER
            .comment("Enable client-side text to speech generation.")
            .define("tts.enabled", true);

    public static final ModConfigSpec.ConfigValue<String> TTS_BACKEND = BUILDER
            .comment("TTS backend: auto, sapi, winrt, or off.")
            .define("tts.backend", "auto");

    public static final ModConfigSpec.ConfigValue<String> TTS_LANGUAGE = BUILDER
            .comment("Preferred TTS language.")
            .define("tts.language", "ja-JP");

    public static final ModConfigSpec.ConfigValue<String> TTS_PREFERRED_VOICE_CONTAINS = BUILDER
            .comment("Preferred voice substring or id. Nanami is recommended when NaturalVoiceSAPIAdapter is installed.")
            .define("tts.preferredVoiceContains", "Nanami");

    public static final ModConfigSpec.ConfigValue<String> TTS_BRIDGE_PATH = BUILDER
            .comment("Optional absolute path to WinTtsBridge.exe. Leave empty to use the bundled bridge extracted from the mod jar.")
            .define("tts.bridgePath", "");

    public static final ModConfigSpec.DoubleValue TTS_RATE = BUILDER
            .comment("TTS rate multiplier passed to the bridge.")
            .defineInRange("tts.rate", 0.92D, 0.5D, 2.0D);

    public static final ModConfigSpec.IntValue TTS_VOLUME = BUILDER
            .comment("TTS output volume passed to the bridge.")
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
