package com.misaka10843.createrailwayannouncer.tts;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.misaka10843.createrailwayannouncer.CreateRailwayAnnouncer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class WindowsBridgeTtsProvider implements TtsProvider {
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "Create Railway Announcer TTS Bridge");
        thread.setDaemon(true);
        return thread;
    });

    private static Path createInputFile(TtsRequest request) throws IOException {
        String suffix = request.ssml() ? ".ssml" : ".txt";
        Path inputFile = Files.createTempFile("cra_tts_input_", suffix);
        Files.writeString(inputFile, request.input(), StandardCharsets.UTF_8);
        inputFile.toFile().deleteOnExit();
        return inputFile;
    }

    private static List<String> buildSynthCommand(Path bridge, TtsRequest request, Path inputFile) {
        List<String> command = new ArrayList<>();

        command.add(bridge.toString());
        command.add("synth");

        command.add("--backend");
        command.add(toBridgeBackend(request.backend()));

        if (request.ssml()) {
            command.add("--ssml-file");
        } else {
            command.add("--text-file");
        }
        command.add(inputFile.toString());

        command.add("--out");
        command.add(request.output().toString());

        command.add("--language");
        command.add(request.language());

        if (request.voiceContains() != null && !request.voiceContains().isBlank()) {
            command.add("--voice-contains");
            command.add(request.voiceContains());
        }

        command.add("--rate");
        command.add(Double.toString(request.rate()));

        command.add("--volume");
        command.add(Integer.toString(request.volume()));

        return command;
    }

    public static String toBridgeBackend(TtsBackend backend) {
        return switch (backend) {
            case AUTO -> "auto";
            case SAPI -> "sapi";
            case WINRT -> "winrt";
            case OFF -> "off";
        };
    }

    private static String readString(JsonObject object, String key, String fallback) {
        if (!object.has(key) || object.get(key).isJsonNull()) {
            return fallback;
        }
        return object.get(key).getAsString();
    }

    @Override
    public String id() {
        return "windows_bridge";
    }

    @Override
    public boolean isAvailable() {
        if (!BridgeExtractor.isWindows()) {
            return false;
        }

        try {
            BridgeExtractor.resolveBridgeExecutable();
            return true;
        } catch (IOException e) {
            CreateRailwayAnnouncer.LOGGER.debug("WinTtsBridge is not available: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public CompletableFuture<TtsResult> synthesize(TtsRequest request) {
        return CompletableFuture.supplyAsync(() -> synthesizeBlocking(request), executor);
    }

    private TtsResult synthesizeBlocking(TtsRequest request) {
        if (request.backend() == TtsBackend.OFF) {
            return TtsResult.failure("TTS backend is off.");
        }

        try {
            Path bridge = BridgeExtractor.resolveBridgeExecutable();

            if (request.output() == null) {
                return TtsResult.failure("Output path is null.");
            }

            Files.createDirectories(request.output().getParent());

            Path inputFile = createInputFile(request);
            List<String> command = buildSynthCommand(bridge, request, inputFile);

            BridgeProcessResult processResult = BridgeProcessRunner.run(command, DEFAULT_TIMEOUT);
            if (processResult.timedOut()) {
                return TtsResult.failure("WinTtsBridge timed out.");
            }

            if (processResult.stdout().isBlank()) {
                return TtsResult.failure("WinTtsBridge returned empty stdout. stderr=" + processResult.stderr());
            }

            JsonObject root = JsonParser.parseString(processResult.stdout()).getAsJsonObject();
            boolean ok = root.has("Ok") && root.get("Ok").getAsBoolean();

            if (!ok || !processResult.success()) {
                String error = readString(root, "Error", "WinTtsBridge failed with exit code " + processResult.exitCode());
                return TtsResult.failure(error + " stderr=" + processResult.stderr());
            }

            String backend = readString(root, "Backend", request.backend().name().toLowerCase(Locale.ROOT));
            String voiceId = null;

            if (root.has("Voice") && root.get("Voice").isJsonObject()) {
                JsonObject voice = root.getAsJsonObject("Voice");
                voiceId = readString(voice, "Id", null);
            }

            return TtsResult.success(request.output(), backend, voiceId);
        } catch (Exception e) {
            CreateRailwayAnnouncer.LOGGER.warn("TTS generation failed", e);
            return TtsResult.failure(e.getMessage());
        }
    }

    public CompletableFuture<BridgeProcessResult> listVoices(TtsBackend backend, String language, boolean naturalOnly) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Path bridge = BridgeExtractor.resolveBridgeExecutable();

                List<String> command = new ArrayList<>();
                command.add(bridge.toString());
                command.add("list-voices");
                command.add("--backend");
                command.add(toBridgeBackend(backend));
                command.add("--language");
                command.add(language);

                if (naturalOnly) {
                    command.add("--natural-only");
                }

                return BridgeProcessRunner.run(command, DEFAULT_TIMEOUT);
            } catch (Exception e) {
                CreateRailwayAnnouncer.LOGGER.warn("Failed to list TTS voices", e);
                return new BridgeProcessResult(-1, "", e.getMessage(), false);
            }
        }, executor);
    }
}