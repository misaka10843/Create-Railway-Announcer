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
import com.misaka10843.createrailwayannouncer.audio.AudioCache;
import com.misaka10843.createrailwayannouncer.audio.AudioFragmentKey;
import com.misaka10843.createrailwayannouncer.audio.AudioFragmentType;
import com.misaka10843.createrailwayannouncer.pack.PhraseEntry;
import com.misaka10843.createrailwayannouncer.pack.VoicePackLoader;
import com.misaka10843.createrailwayannouncer.pack.VoicePackManager;
import com.misaka10843.createrailwayannouncer.sequence.SequenceResolver;
import com.misaka10843.createrailwayannouncer.sequence.SequenceTemplate;
import com.misaka10843.createrailwayannouncer.audio.AudioFragmentResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public final class CreateRailwayAnnouncerCommands {
    private static final WindowsBridgeTtsProvider WINDOWS_TTS = new WindowsBridgeTtsProvider();

    private static AudioCache audioCache;
    private static SequenceResolver sequenceResolver;

    private static AudioCache audioCache() {
        if (audioCache == null) {
            audioCache = new AudioCache(WINDOWS_TTS);
        }
        return audioCache;
    }

    private static SequenceResolver sequenceResolver() {
        if (sequenceResolver == null) {
            sequenceResolver = new SequenceResolver(audioCache());
        }
        return sequenceResolver;
    }

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
                                        .executes(context -> testSsml(context.getSource())))
                                .then(Commands.literal("phrase")
                                        .then(Commands.argument("id", StringArgumentType.word())
                                                .then(Commands.argument("text", StringArgumentType.greedyString())
                                                        .executes(context -> cacheFragment(
                                                                context.getSource(),
                                                                AudioFragmentType.COMMON,
                                                                StringArgumentType.getString(context, "id"),
                                                                "default",
                                                                StringArgumentType.getString(context, "text"),
                                                                false
                                                        )))))
                                .then(Commands.literal("station")
                                        .then(Commands.argument("id", StringArgumentType.word())
                                                .then(Commands.argument("variant", StringArgumentType.word())
                                                        .then(Commands.argument("text", StringArgumentType.greedyString())
                                                                .executes(context -> cacheFragment(
                                                                        context.getSource(),
                                                                        AudioFragmentType.STATION,
                                                                        StringArgumentType.getString(context, "id"),
                                                                        StringArgumentType.getString(context, "variant"),
                                                                        StringArgumentType.getString(context, "text"),
                                                                        false
                                                                ))))))
                                .then(Commands.literal("line")
                                        .then(Commands.argument("id", StringArgumentType.word())
                                                .then(Commands.argument("variant", StringArgumentType.word())
                                                        .then(Commands.argument("text", StringArgumentType.greedyString())
                                                                .executes(context -> cacheFragment(
                                                                        context.getSource(),
                                                                        AudioFragmentType.LINE,
                                                                        StringArgumentType.getString(context, "id"),
                                                                        StringArgumentType.getString(context, "variant"),
                                                                        StringArgumentType.getString(context, "text"),
                                                                        false
                                                                ))))))
                        )
                        .then(Commands.literal("pack")
                                .then(Commands.literal("reload")
                                        .executes(context -> reloadPack(context.getSource())))
                                .then(Commands.literal("info")
                                        .executes(context -> packInfo(context.getSource())))
                                .then(Commands.literal("phrase")
                                        .then(Commands.argument("id", StringArgumentType.word())
                                                .executes(context -> cachePackPhrase(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, "id")
                                                )))))
                        .then(Commands.literal("sequence")
                                .then(Commands.literal("test_next_stop")
                                        .executes(context -> testSequence(
                                                context.getSource(),
                                                "onboard_next_stop_test"
                                        )))
                                .then(Commands.literal("resolve")
                                        .then(Commands.argument("id", StringArgumentType.word())
                                                .executes(context -> testSequence(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, "id")
                                                )))))
        );
    }

    private static int testSequence(CommandSourceStack source, String sequenceId) {
        SequenceTemplate template = VoicePackManager.sequences().get(sequenceId).orElse(null);

        if (template == null) {
            source.sendFailure(Component.literal("Sequence not found: " + sequenceId + ". Use /cra pack reload first."));
            return 0;
        }

        source.sendSuccess(() -> Component.literal(
                "Resolving sequence: " + template.id() + ", items=" + template.items().size()
        ).withStyle(ChatFormatting.GRAY), false);

        sequenceResolver().resolveAudioFragments(template).thenAccept(results -> {
            long okCount = results.stream().filter(AudioFragmentResult::ok).count();

            source.sendSuccess(() -> Component.literal(
                    "Sequence resolved: " + okCount + "/" + results.size() + " audio fragments ready."
            ).withStyle(ChatFormatting.GREEN), false);

            for (AudioFragmentResult result : results) {
                if (result.ok()) {
                    source.sendSuccess(() -> Component.literal("OK: " + result.audioPath()).withStyle(ChatFormatting.GRAY), false);
                } else {
                    source.sendFailure(Component.literal("FAILED: " + result.error()));
                }
            }
        });

        return 1;
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

    private static int cacheFragment(
            CommandSourceStack source,
            AudioFragmentType type,
            String id,
            String variant,
            String input,
            boolean ssml
    ) {
        if (input == null || input.isBlank()) {
            source.sendFailure(Component.literal("Fragment text is empty."));
            return 0;
        }

        AudioFragmentKey key = new AudioFragmentKey(
                type,
                id,
                variant,
                readBackend(),
                ClientConfig.TTS_LANGUAGE.get(),
                ClientConfig.TTS_PREFERRED_VOICE_CONTAINS.get(),
                ClientConfig.TTS_RATE.get(),
                ClientConfig.TTS_VOLUME.get(),
                input,
                ssml
        );

        Path output = audioCache().resolvePath(key);

        source.sendSuccess(() -> Component.literal("Resolving audio fragment: " + type + " " + id + "/" + variant)
                .withStyle(ChatFormatting.GRAY), false);

        audioCache().resolveOrGenerate(key).thenAccept(result -> {
            if (!result.ok()) {
                source.sendFailure(Component.literal("Audio fragment failed: " + result.error()));
                return;
            }

            long size = -1L;
            try {
                if (Files.isRegularFile(result.audioPath())) {
                    size = Files.size(result.audioPath());
                }
            } catch (Exception ignored) {
            }

            long finalSize = size;
            source.sendSuccess(() -> Component.literal(
                    "Audio fragment ready: " + result.audioPath() + " (" + result.format() + ", " + finalSize + " bytes)"
            ).withStyle(ChatFormatting.GREEN), false);
        });

        return 1;
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

    // 音效包相关
    private static int reloadPack(CommandSourceStack source) {
        VoicePackLoader.reloadDefaultPack();

        VoicePackManager.activePack().ifPresentOrElse(pack -> {
            source.sendSuccess(() -> Component.literal(
                    "Loaded voice pack: " + pack.name() + " (" + pack.id() + "), phrases=" + VoicePackManager.phrases().size()
            ).withStyle(ChatFormatting.GREEN), false);
        }, () -> {
            source.sendFailure(Component.literal("No voice pack loaded. Check latest.log."));
        });

        return 1;
    }

    private static int packInfo(CommandSourceStack source) {
        VoicePackManager.activePack().ifPresentOrElse(pack -> {
            source.sendSuccess(() -> Component.literal("Voice pack: " + pack.name()).withStyle(ChatFormatting.GREEN), false);
            source.sendSuccess(() -> Component.literal("ID: " + pack.id()).withStyle(ChatFormatting.GRAY), false);
            source.sendSuccess(() -> Component.literal("Version: " + pack.version()).withStyle(ChatFormatting.GRAY), false);
            source.sendSuccess(() -> Component.literal("Default language: " + pack.defaultLanguage()).withStyle(ChatFormatting.GRAY), false);
            source.sendSuccess(() -> Component.literal("Phrases: " + VoicePackManager.phrases().size()).withStyle(ChatFormatting.GRAY), false);
            source.sendSuccess(() -> Component.literal("Sequences: " + VoicePackManager.sequences().size()).withStyle(ChatFormatting.GRAY), false);
        }, () -> {
            source.sendFailure(Component.literal("No voice pack loaded. Use /cra pack reload first."));
        });

        return 1;
    }

    private static int cachePackPhrase(CommandSourceStack source, String phraseId) {
        PhraseEntry entry = VoicePackManager.phrases().get(phraseId).orElse(null);

        if (entry == null) {
            source.sendFailure(Component.literal("Phrase not found: " + phraseId));
            return 0;
        }

        return cacheFragment(
                source,
                AudioFragmentType.COMMON,
                entry.id(),
                "default",
                entry.text(),
                entry.ssml()
        );
    }
}