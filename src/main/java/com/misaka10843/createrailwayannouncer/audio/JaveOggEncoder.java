package com.misaka10843.createrailwayannouncer.audio;

import com.misaka10843.createrailwayannouncer.CreateRailwayAnnouncer;
import ws.schild.jave.Encoder;
import ws.schild.jave.EncoderException;
import ws.schild.jave.MultimediaObject;
import ws.schild.jave.encode.AudioAttributes;
import ws.schild.jave.encode.EncodingAttributes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class JaveOggEncoder implements AudioEncoder {
    private static final int DEFAULT_BIT_RATE = 64_000;
    private static final int DEFAULT_SAMPLE_RATE = 44_100;

    @Override
    public String id() {
        return "jave_ogg_vorbis";
    }

    @Override
    public AudioEncodeResult encodeWavToOgg(Path wavInput, Path oggOutput) {
        if (wavInput == null || !Files.isRegularFile(wavInput)) {
            return AudioEncodeResult.failure("Input wav does not exist: " + wavInput);
        }

        if (oggOutput == null) {
            return AudioEncodeResult.failure("Output ogg path is null.");
        }

        try {
            Files.createDirectories(oggOutput.getParent());

            AudioAttributes audio = new AudioAttributes();
            audio.setCodec("libvorbis");
            audio.setBitRate(DEFAULT_BIT_RATE);
            audio.setSamplingRate(DEFAULT_SAMPLE_RATE);

            EncodingAttributes attributes = new EncodingAttributes();
            attributes.setOutputFormat("ogg");
            attributes.setAudioAttributes(audio);

            Encoder encoder = new Encoder();
            encoder.encode(new MultimediaObject(wavInput.toFile()), oggOutput.toFile(), attributes);

            if (!Files.isRegularFile(oggOutput)) {
                return AudioEncodeResult.failure("Encoder finished, but ogg output was not created: " + oggOutput);
            }

            long size = Files.size(oggOutput);
            if (size <= 0L) {
                return AudioEncodeResult.failure("Encoder created empty ogg file: " + oggOutput);
            }

            return AudioEncodeResult.success(oggOutput);
        } catch (EncoderException | IOException e) {
            CreateRailwayAnnouncer.LOGGER.warn("Failed to encode wav to ogg: {} -> {}", wavInput, oggOutput, e);
            try {
                Files.deleteIfExists(oggOutput);
            } catch (IOException ignored) {
            }
            return AudioEncodeResult.failure(e.getMessage());
        }
    }
}