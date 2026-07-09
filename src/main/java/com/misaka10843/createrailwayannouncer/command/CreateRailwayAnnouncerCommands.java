package com.misaka10843.createrailwayannouncer.command;

import com.misaka10843.createrailwayannouncer.CreateRailwayAnnouncer;
import com.misaka10843.createrailwayannouncer.config.ClientConfig;
import com.misaka10843.createrailwayannouncer.tts.BridgeProcessResult;
import com.misaka10843.createrailwayannouncer.tts.TtsBackend;
import com.misaka10843.createrailwayannouncer.tts.TtsRequest;
import com.misaka10843.createrailwayannouncer.tts.TtsResult;
import com.misaka10843.createrailwayannouncer.tts.WindowsBridgeTtsProvider;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public final class CreateRailwayAnnouncerCommands {
    private static final WindowsBridgeTtsProvider WINDOWS_TTS = new WindowsBridgeTtsProvider();

    private CreateRailwayAnnouncerCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("cra")
                        .then(Commands.literal("tts")
                                .then(Commands.literal("voices")
                                        .executes(context -> listVoices(context.getSource(), false))
                                        .then(Commands.literal("natural")
                                                .executes(context -> listVoices(context.getSource(), true))))
                                .then(Commands.literal("test_text")
                                        .then(Commands.argument("text", StringArgumentType.greedyString())
                                                .executes(context -> testText(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, "text")
                                                ))))
                                .then(Commands.literal("test_ssml")
                                        .executes(context -> testSsml(context.getSource()))))
        );
    }

    private static int listVoices(CommandSourceStack source, boolean naturalOnly) {
        if (!source.getServer().isSameThread()) {
            source.sendFailure(Component.literal("This command must be run on the main server thread."));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("Listing TTS voices...").withStyle(ChatFormatting.GRAY), false);

        TtsBackend backend = readBackend();
        String language = ClientConfig.TTS_LANGUAGE.get();

        CompletableFuture<BridgeProcessResult> future = WINDOWS_TTS.listVoices(backend, language, naturalOnly);
        future.thenAccept(result -> {
            if (!result.success()) {
                source.sendFailure(Component.literal("Failed to list voices: " + result.stderr()));
                return;
            }

            String stdout = result.stdout();
            CreateRailwayAnnouncer.LOGGER.info("WinTtsBridge voices: {}", stdout);

            source.sendSuccess(() -> Component.literal("Voice list written to log. Check latest.log.").withStyle(ChatFormatting.GREEN), false);

            String preview = stdout.length() > 900 ? stdout.substring(0, 900) + "..." : stdout;
            source.sendSuccess(() -> Component.literal(preview).withStyle(ChatFormatting.GRAY), false);
        });

        return 1;
    }

    private static int testText(CommandSourceStack source, String text) {
        if (text == null || text.isBlank()) {
            source.sendFailure(Component.literal("Text is empty."));
            return 0;
        }

        Path output = testOutputPath("test_text.wav");

        TtsRequest request = new TtsRequest(
                readBackend(),
                ClientConfig.TTS_LANGUAGE.get(),
                ClientConfig.TTS_PREFERRED_VOICE_CONTAINS.get(),
                ClientConfig.TTS_RATE.get(),
                ClientConfig.TTS_VOLUME.get(),
                text,
                false,
                output
        );

        runTtsTest(source, request);
        return 1;
    }

    private static int testSsml(CommandSourceStack source) {
        String ssml = """
                <speak version="1.0" xml:lang="ja-JP">
                  次は<break time="500ms"/>
                  有楽町<break time="300ms"/>
                  有楽町です<break time="300ms"/>
                  お出口は<break time="180ms"/>
                  左側です<break time="450ms"/>
                  地下鉄日比谷線<break time="180ms"/>
                  地下鉄有楽町線は<break time="180ms"/>
                  お乗り換えです
                </speak>
                """;

        Path output = testOutputPath("test_ssml.wav");

        TtsRequest request = new TtsRequest(
                readBackend(),
                ClientConfig.TTS_LANGUAGE.get(),
                ClientConfig.TTS_PREFERRED_VOICE_CONTAINS.get(),
                ClientConfig.TTS_RATE.get(),
                ClientConfig.TTS_VOLUME.get(),
                ssml,
                true,
                output
        );

        runTtsTest(source, request);
        return 1;
    }

    private static void runTtsTest(CommandSourceStack source, TtsRequest request) {
        source.sendSuccess(() -> Component.literal("Generating TTS test audio...").withStyle(ChatFormatting.GRAY), false);

        WINDOWS_TTS.synthesize(request).thenAccept(result -> {
            if (!result.ok()) {
                source.sendFailure(Component.literal("TTS failed: " + result.error()));
                return;
            }

            long size = -1L;
            try {
                if (Files.isRegularFile(result.output())) {
                    size = Files.size(result.output());
                }
            } catch (Exception ignored) {
            }

            long finalSize = size;
            source.sendSuccess(() -> Component.literal(
                    "TTS generated: " + result.output() + " (" + finalSize + " bytes)"
            ).withStyle(ChatFormatting.GREEN), false);
        });
    }

    private static Path testOutputPath(String fileName) {
        return FMLPaths.CONFIGDIR.get()
                .resolve(CreateRailwayAnnouncer.MODID)
                .resolve("test")
                .resolve(fileName);
    }

    private static TtsBackend readBackend() {
        String value = ClientConfig.TTS_BACKEND.get().trim().toUpperCase(Locale.ROOT);
        try {
            return TtsBackend.valueOf(value);
        } catch (IllegalArgumentException e) {
            return TtsBackend.AUTO;
        }
    }
}