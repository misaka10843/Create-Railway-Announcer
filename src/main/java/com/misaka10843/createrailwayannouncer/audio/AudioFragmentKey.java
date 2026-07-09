package com.misaka10843.createrailwayannouncer.audio;

import com.misaka10843.createrailwayannouncer.tts.TtsBackend;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

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
        boolean ssml
) {
    private static String sanitize(String value) {
        String normalized = value == null ? "unknown" : value.trim().toLowerCase(Locale.ROOT);
        normalized = normalized.replace('\\', '_').replace('/', '_').replace(':', '_');
        normalized = normalized.replaceAll("[^a-z0-9._\\-]+", "_");
        normalized = normalized.replaceAll("_+", "_");
        normalized = normalized.replaceAll("^_+", "");
        normalized = normalized.replaceAll("_+$", "");
        return normalized.isBlank() ? "unknown" : normalized;
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    public String safeId() {
        return sanitize(id);
    }

    public String safeVariant() {
        return sanitize(variant == null || variant.isBlank() ? "default" : variant);
    }

    public String inputHash() {
        return sha256(input + "\nssml=" + ssml).substring(0, 16);
    }

    public String fileName() {
        return safeId() + "_" + safeVariant() + "_" + inputHash() + ".ogg";
    }

    public String tempWavFileName() {
        return safeId() + "_" + safeVariant() + "_" + inputHash() + ".tmp.wav";
    }

    public String rateVolumeDirectory() {
        int ratePercent = (int) Math.round(rate * 100.0D);
        return "rate_" + ratePercent + "_volume_" + volume;
    }

    public String voiceDirectory() {
        String voice = voiceContains == null || voiceContains.isBlank() ? "auto" : voiceContains;
        return sanitize(voice);
    }
}