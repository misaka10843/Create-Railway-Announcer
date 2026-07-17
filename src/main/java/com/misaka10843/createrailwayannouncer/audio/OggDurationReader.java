package com.misaka10843.createrailwayannouncer.audio;

import com.misaka10843.createrailwayannouncer.CreateRailwayAnnouncer;
import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBVorbis;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.libc.LibCStdlib;

import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class OggDurationReader {
    private static final Map<Path, Long> CACHE = new ConcurrentHashMap<>();

    private OggDurationReader() {
    }

    public static long durationMs(Path oggPath) {
        if (oggPath == null) {
            return 0L;
        }

        Path normalized = oggPath.toAbsolutePath().normalize();
        return CACHE.computeIfAbsent(normalized, OggDurationReader::readDurationMsSafe);
    }

    public static void clearCache() {
        CACHE.clear();
    }

    private static long readDurationMsSafe(Path oggPath) {
        try {
            return readDurationMs(oggPath);
        } catch (Throwable t) {
            CreateRailwayAnnouncer.LOGGER.warn(
                    "Failed to read OGG duration: {}",
                    oggPath,
                    t
            );
            return 0L;
        }
    }

    private static long readDurationMs(Path oggPath) {
        if (!Files.isRegularFile(oggPath)) {
            return 0L;
        }

        String name = oggPath.getFileName().toString().toLowerCase(Locale.ROOT);
        if (!name.endsWith(".ogg")) {
            return 0L;
        }

        IntBuffer channelsBuffer = BufferUtils.createIntBuffer(1);
        IntBuffer sampleRateBuffer = BufferUtils.createIntBuffer(1);
        ShortBuffer pcm = null;

        try {
            pcm = STBVorbis.stb_vorbis_decode_filename(
                    oggPath.toAbsolutePath().toString(),
                    channelsBuffer,
                    sampleRateBuffer
            );

            if (pcm == null) {
                return 0L;
            }

            int channels = channelsBuffer.get(0);
            int sampleRate = sampleRateBuffer.get(0);
            int totalSamples = pcm.remaining();

            if (channels <= 0 || sampleRate <= 0 || totalSamples <= 0) {
                return 0L;
            }

            long frames = totalSamples / channels;
            return Math.max(1L, Math.round(frames * 1000.0D / sampleRate));
        } finally {
            freeStbPcm(pcm);
        }
    }

    private static void freeStbPcm(ShortBuffer pcm) {
        if (pcm == null) {
            return;
        }

        try {
            LibCStdlib.nfree(MemoryUtil.memAddress(pcm));
        } catch (Throwable t) {
            CreateRailwayAnnouncer.LOGGER.warn(
                    "Failed to free STB decoded PCM buffer while reading duration",
                    t
            );
        }
    }
}