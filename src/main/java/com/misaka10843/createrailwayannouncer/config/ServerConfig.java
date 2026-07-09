package com.misaka10843.createrailwayannouncer.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class ServerConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ENABLE_ONBOARD_ANNOUNCEMENTS = BUILDER
            .comment("Enable onboard train announcements.")
            .define("events.enableOnboardAnnouncements", true);

    public static final ModConfigSpec.BooleanValue ENABLE_PLATFORM_ANNOUNCEMENTS = BUILDER
            .comment("Enable platform and station announcements.")
            .define("events.enablePlatformAnnouncements", true);

    public static final ModConfigSpec.IntValue DEFAULT_STATION_HORIZONTAL_RANGE = BUILDER
            .comment("Default station horizontal announcement range, centered on the Create Station block.")
            .defineInRange("stationAudio.horizontalRange", 64, 8, 256);

    public static final ModConfigSpec.IntValue DEFAULT_STATION_VERTICAL_RANGE = BUILDER
            .comment("Default station vertical announcement range, centered on the Create Station block.")
            .defineInRange("stationAudio.verticalRange", 24, 4, 128);

    public static final ModConfigSpec.IntValue APPROACH_CHECK_INTERVAL_TICKS = BUILDER
            .comment("Low-frequency approach check interval in ticks. Do not set too low on servers.")
            .defineInRange("performance.approachCheckIntervalTicks", 20, 5, 200);

    public static final ModConfigSpec.IntValue ANNOUNCEMENT_COOLDOWN_TICKS = BUILDER
            .comment("Cooldown for duplicate train/station/event announcements.")
            .defineInRange("performance.announcementCooldownTicks", 600, 20, 72000);

    public static final ModConfigSpec.BooleanValue OP_REQUIRED_TO_EDIT_STATION = BUILDER
            .comment("Require OP permission to edit station announcement configuration on servers.")
            .define("permissions.opRequiredToEditStation", true);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private ServerConfig() {
    }
}
