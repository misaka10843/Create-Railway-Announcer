package com.misaka10843.createrailwayannouncer.client.audio;

import com.misaka10843.createrailwayannouncer.audio.AudioChannel;
import com.misaka10843.createrailwayannouncer.config.ClientConfig;

public final class ClientAudioSettings {
    private ClientAudioSettings() {
    }

    public static float gainFor(AudioChannel channel) {
        double master = getOrDefault(ClientConfig.MASTER_VOLUME, 1.0D);
        double channelVolume = switch (channel) {
            case ONBOARD_VOICE -> getOrDefault(ClientConfig.ONBOARD_VOLUME, 1.0D);
            case PLATFORM_VOICE -> getOrDefault(ClientConfig.PLATFORM_VOLUME, 1.0D);
            case MELODY -> getOrDefault(ClientConfig.MELODY_VOLUME, 0.8D);
            case DOOR_CHIME -> getOrDefault(ClientConfig.CHIME_VOLUME, 0.9D);
            default -> 1.0D;
        };

        return clampGain(master * channelVolume);
    }

    private static double getOrDefault(net.neoforged.neoforge.common.ModConfigSpec.DoubleValue value, double fallback) {
        try {
            return value.get();
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static float clampGain(double gain) {
        if (gain <= 0.0D) {
            return 0.0F;
        }

        if (gain >= 1.0D) {
            return 1.0F;
        }

        return (float) gain;
    }
}