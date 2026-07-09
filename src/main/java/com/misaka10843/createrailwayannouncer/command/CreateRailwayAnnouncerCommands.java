package com.misaka10843.createrailwayannouncer.command;

import com.misaka10843.createrailwayannouncer.CreateRailwayAnnouncer;
import com.misaka10843.createrailwayannouncer.announcement.AnnouncementEventType;
import com.misaka10843.createrailwayannouncer.announcement.ServerAnnouncementCooldown;
import com.misaka10843.createrailwayannouncer.announcement.ServerAnnouncementDispatcher;
import com.misaka10843.createrailwayannouncer.announcement.ServerAnnouncementRequest;
import com.misaka10843.createrailwayannouncer.audio.*;
import com.misaka10843.createrailwayannouncer.client.runtime.AnnouncementPlaybackRequest;
import com.misaka10843.createrailwayannouncer.client.runtime.ClientAnnouncementRuntime;
import com.misaka10843.createrailwayannouncer.client.runtime.ClientAnnouncementServices;
import com.misaka10843.createrailwayannouncer.config.ClientConfig;
import com.misaka10843.createrailwayannouncer.config.LineConfig;
import com.misaka10843.createrailwayannouncer.config.StationConfig;
import com.misaka10843.createrailwayannouncer.config.StationLineConfigStore;
import com.misaka10843.createrailwayannouncer.pack.PhraseEntry;
import com.misaka10843.createrailwayannouncer.pack.VoicePackLoader;
import com.misaka10843.createrailwayannouncer.pack.VoicePackManager;
import com.misaka10843.createrailwayannouncer.playback.LoggingSequenceAudioBackend;
import com.misaka10843.createrailwayannouncer.playback.PlaybackScheduler;
import com.misaka10843.createrailwayannouncer.playback.PlaybackSession;
import com.misaka10843.createrailwayannouncer.playback.SequenceAudioBackend;
import com.misaka10843.createrailwayannouncer.sequence.ResolvedSequence;
import com.misaka10843.createrailwayannouncer.sequence.ResolvedSequenceItem;
import com.misaka10843.createrailwayannouncer.sequence.SequenceResolver;
import com.misaka10843.createrailwayannouncer.sequence.SequenceTemplate;
import com.misaka10843.createrailwayannouncer.tts.BridgeProcessResult;
import com.misaka10843.createrailwayannouncer.tts.TtsBackend;
import com.misaka10843.createrailwayannouncer.tts.TtsRequest;
import com.misaka10843.createrailwayannouncer.tts.WindowsBridgeTtsProvider;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class CreateRailwayAnnouncerCommands {
    private static final WindowsBridgeTtsProvider WINDOWS_TTS = new WindowsBridgeTtsProvider();
    private static final PlaybackScheduler PLAYBACK_SCHEDULER = new PlaybackScheduler();
    private static final UUID DEBUG_TRAIN_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID DEBUG_STATION_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000201");
    private static AudioCache audioCache;
    private static SequenceResolver sequenceResolver;

    private CreateRailwayAnnouncerCommands() {
    }

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
                                                ))))
                                .then(Commands.literal("print")
                                        .then(Commands.literal("test_next_stop")
                                                .executes(context -> printSequence(
                                                        context.getSource(),
                                                        "onboard_next_stop_test"
                                                )))
                                        .then(Commands.argument("id", StringArgumentType.word())
                                                .executes(context -> printSequence(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, "id")
                                                ))))
                                .then(Commands.literal("dry_play")
                                        .then(Commands.literal("test_next_stop")
                                                .executes(context -> dryPlaySequence(
                                                        context.getSource(),
                                                        "onboard_next_stop_test"
                                                )))
                                        .then(Commands.argument("id", StringArgumentType.word())
                                                .executes(context -> dryPlaySequence(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, "id")
                                                ))))
                                .then(Commands.literal("play")
                                        .then(Commands.literal("test_next_stop")
                                                .executes(context -> playSequence(
                                                        context.getSource(),
                                                        "onboard_next_stop_test"
                                                )))
                                        .then(Commands.argument("id", StringArgumentType.word())
                                                .executes(context -> playSequence(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, "id")
                                                ))))
                                .then(Commands.literal("stop")
                                        .executes(context -> stopPlayback(context.getSource())))
                                .then(Commands.literal("status")
                                        .executes(context -> playbackStatus(context.getSource())))
                        )
                        .then(Commands.literal("announce")
                                .then(Commands.literal("test_next_stop")
                                        .executes(context -> announcePlay(
                                                context.getSource(),
                                                "onboard_next_stop_test"
                                        )))
                                .then(Commands.literal("send_packet_next_stop")
                                        .executes(context -> sendAnnouncementPacket(
                                                context.getSource(),
                                                AnnouncementEventType.ONBOARD_NEXT_STOP
                                        )))
                                .then(Commands.literal("send_packet_door_closing")
                                        .executes(context -> sendAnnouncementPacket(
                                                context.getSource(),
                                                AnnouncementEventType.PLATFORM_DOOR_CLOSING
                                        )))
                                .then(Commands.literal("send_packet_onboard_door_closing")
                                        .executes(context -> sendAnnouncementPacket(
                                                context.getSource(),
                                                AnnouncementEventType.DOOR_CLOSING
                                        )))
                                .then(Commands.literal("play")
                                        .then(Commands.argument("id", StringArgumentType.word())
                                                .executes(context -> announcePlay(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, "id")
                                                ))))
                                .then(Commands.literal("stop")
                                        .executes(context -> stopPlayback(context.getSource())))
                                .then(Commands.literal("status")
                                        .executes(context -> playbackStatus(context.getSource())))
                                .then(Commands.literal("cooldown_clear")
                                        .executes(context -> clearAnnouncementCooldown(context.getSource())))
                                .then(Commands.literal("cooldown_info")
                                        .executes(context -> announcementCooldownInfo(context.getSource())))
                                .then(Commands.literal("send_packet_next_stop_far")
                                        .executes(context -> sendFarAnnouncementPacket(
                                                context.getSource(),
                                                AnnouncementEventType.ONBOARD_NEXT_STOP
                                        )))
                        )
                        .then(Commands.literal("data")
                                .then(Commands.literal("reload")
                                        .executes(context -> reloadData(context.getSource())))
                                .then(Commands.literal("info")
                                        .executes(context -> dataInfo(context.getSource())))
                                .then(Commands.literal("station")
                                        .then(Commands.argument("id", StringArgumentType.word())
                                                .executes(context -> showStationData(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, "id")
                                                ))))
                                .then(Commands.literal("line")
                                        .then(Commands.argument("id", StringArgumentType.word())
                                                .executes(context -> showLineData(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, "id")
                                                ))))
                        )

        );
    }

    private static int clearAnnouncementCooldown(CommandSourceStack source) {
        int cleared = ServerAnnouncementCooldown.clear();

        source.sendSuccess(() -> Component.literal(
                "Announcement cooldown cleared. entries=" + cleared
        ).withStyle(ChatFormatting.GREEN), false);

        return 1;
    }

    private static int announcementCooldownInfo(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal(
                "Announcement cooldown entries="
                        + ServerAnnouncementCooldown.size()
                        + ", cooldownTicks="
                        + com.misaka10843.createrailwayannouncer.config.ServerConfig.ANNOUNCEMENT_COOLDOWN_TICKS.get()
        ).withStyle(ChatFormatting.GREEN), false);

        return 1;
    }

    private static int sendFarAnnouncementPacket(
            CommandSourceStack source,
            AnnouncementEventType eventType
    ) throws CommandSyntaxException {
        source.getPlayerOrException();

        ServerLevel level = source.getLevel();

        int priority = priorityFor(eventType);
        AudioChannel channel = channelFor(eventType);

        BlockPos sourcePos = BlockPos.containing(source.getPosition());
        BlockPos farPos = sourcePos.offset(512, 0, 512);

        ServerAnnouncementRequest request = ServerAnnouncementRequest.of(
                eventType,
                DEBUG_TRAIN_ID,
                "debug_train_far",
                DEBUG_STATION_ID,
                farPos,
                "debug_current_station",
                "yurakucho",
                "debug_destination_station",
                channel,
                priority
        );

        ServerAnnouncementDispatcher.DispatchResult result =
                ServerAnnouncementDispatcher.dispatchToNearbyPlayers(level, request);

        if (!result.accepted()) {
            if (result.suppressed()) {
                source.sendSuccess(() -> Component.literal(
                        "Far announcement suppressed by cooldown: event="
                                + eventType
                                + ", remainingTicks="
                                + result.remainingCooldownTicks()
                ).withStyle(ChatFormatting.YELLOW), false);
                return 1;
            }

            source.sendFailure(Component.literal(
                    "Far announcement dispatch rejected: " + result.message()
            ));
            return 0;
        }

        source.sendSuccess(() -> Component.literal(
                "Dispatched far announcement: event="
                        + eventType
                        + ", center="
                        + farPos
                        + ", range="
                        + result.horizontalRange()
                        + "x"
                        + result.verticalRange()
                        + ", players="
                        + result.sentPlayers()
                        + ". Expected players=0 unless someone is near that position."
        ).withStyle(ChatFormatting.YELLOW), false);

        return 1;
    }

    private static int reloadData(CommandSourceStack source) {
        StationLineConfigStore.reload();

        source.sendSuccess(() -> Component.literal(
                "Station data reloaded. stations="
                        + StationLineConfigStore.stations().size()
                        + ", lines="
                        + StationLineConfigStore.lines().size()
        ).withStyle(ChatFormatting.GREEN), false);

        return 1;
    }

    private static int dataInfo(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal(
                "Station data: stations="
                        + StationLineConfigStore.stations().size()
                        + ", lines="
                        + StationLineConfigStore.lines().size()
                        + ", path="
                        + StationLineConfigStore.root()
        ).withStyle(ChatFormatting.GREEN), false);

        return 1;
    }

    private static int showStationData(CommandSourceStack source, String id) {
        StationConfig station = StationLineConfigStore.station(id).orElse(null);

        if (station == null) {
            source.sendFailure(Component.literal("Station not found: " + id));
            return 0;
        }

        source.sendSuccess(() -> Component.literal(
                "Station: "
                        + station.getCustomId()
                        + ", enabled="
                        + station.isEnabled()
                        + ", display.ja_jp="
                        + station.getDisplay().getOrDefault("ja_jp", "")
                        + ", reading.ja_jp="
                        + station.getReading().getOrDefault("ja_jp", "")
                        + ", doorSide="
                        + station.getDoorSide()
                        + ", platform="
                        + station.getPlatform()
                        + ", transfers="
                        + station.getTransferLineIds()
        ).withStyle(ChatFormatting.GREEN), false);

        return 1;
    }

    private static int showLineData(CommandSourceStack source, String id) {
        LineConfig line = StationLineConfigStore.line(id).orElse(null);

        if (line == null) {
            source.sendFailure(Component.literal("Line not found: " + id));
            return 0;
        }

        source.sendSuccess(() -> Component.literal(
                "Line: "
                        + line.getId()
                        + ", display.ja_jp="
                        + line.getDisplay().getOrDefault("ja_jp", "")
                        + ", reading.ja_jp="
                        + line.getReading().getOrDefault("ja_jp", "")
                        + ", short.ja_jp="
                        + line.getShortName().getOrDefault("ja_jp", "")
        ).withStyle(ChatFormatting.GREEN), false);

        return 1;
    }

    private static int sendAnnouncementPacket(
            CommandSourceStack source,
            AnnouncementEventType eventType
    ) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = source.getLevel();

        int priority = priorityFor(eventType);
        AudioChannel channel = channelFor(eventType);
        BlockPos pos = BlockPos.containing(source.getPosition());

        ServerAnnouncementRequest request = ServerAnnouncementRequest.of(
                eventType,
                DEBUG_TRAIN_ID,
                "debug_train",
                DEBUG_STATION_ID,
                pos,
                "debug_current_station",
                "yurakucho",
                "debug_destination_station",
                channel,
                priority
        );

        ServerAnnouncementDispatcher.DispatchResult result =
                ServerAnnouncementDispatcher.dispatchToNearbyPlayers(level, request);

        if (!result.accepted()) {
            if (result.suppressed()) {
                source.sendSuccess(() -> Component.literal(
                        "Announcement suppressed by cooldown: event="
                                + eventType
                                + ", remainingTicks="
                                + result.remainingCooldownTicks()
                                + ", remainingSeconds="
                                + String.format("%.1f", result.remainingCooldownTicks() / 20.0D)
                ).withStyle(ChatFormatting.YELLOW), false);
                return 1;
            }

            source.sendFailure(Component.literal(
                    "Announcement dispatch rejected: " + result.message()
            ));
            return 0;
        }

        source.sendSuccess(() -> Component.literal(
                "Dispatched announcement: event="
                        + eventType
                        + ", channel="
                        + channel
                        + ", priority="
                        + priority
                        + ", range="
                        + result.horizontalRange()
                        + "x"
                        + result.verticalRange()
                        + ", players="
                        + result.sentPlayers()
                        + ", selfInRange="
                        + isPlayerInRangeForDebug(player, pos, result.horizontalRange(), result.verticalRange())
        ).withStyle(ChatFormatting.GREEN), false);

        return 1;
    }

    private static int priorityFor(AnnouncementEventType eventType) {
        return switch (eventType) {
            case PLATFORM_DOOR_CLOSING, DOOR_CLOSING, SAFETY_NOTICE -> 90;
            case PLATFORM_DEPARTURE_MELODY -> 75;
            default -> 70;
        };
    }

    private static AudioChannel channelFor(AnnouncementEventType eventType) {
        return switch (eventType) {
            case PLATFORM_DOOR_CLOSING,
                 PLATFORM_APPROACH,
                 PLATFORM_ARRIVAL,
                 PLATFORM_PRE_DEPARTURE,
                 PLATFORM_DEPARTED -> AudioChannel.PLATFORM_VOICE;

            case PLATFORM_DEPARTURE_MELODY -> AudioChannel.MELODY;

            case DOOR_OPENING -> AudioChannel.DOOR_CHIME;

            case DOOR_CLOSING -> AudioChannel.ONBOARD_VOICE;

            default -> AudioChannel.ONBOARD_VOICE;
        };
    }

    private static boolean isPlayerInRangeForDebug(
            ServerPlayer player,
            BlockPos center,
            int horizontalRange,
            int verticalRange
    ) {
        BlockPos playerPos = player.blockPosition();

        int dx = Math.abs(playerPos.getX() - center.getX());
        int dz = Math.abs(playerPos.getZ() - center.getZ());
        int dy = Math.abs(playerPos.getY() - center.getY());

        return dx <= horizontalRange
                && dz <= horizontalRange
                && dy <= verticalRange;
    }

    private static int announcePlay(CommandSourceStack source, String sequenceId) {
        source.sendSuccess(() -> Component.literal(
                "Submitting announcement playback request: " + sequenceId
        ).withStyle(ChatFormatting.GRAY), false);

        playAnnouncementRequest(source, AnnouncementPlaybackRequest.sequenceOnly(sequenceId));
        return 1;
    }

    private static int playSequence(CommandSourceStack source, String sequenceId) {
        source.sendSuccess(() -> Component.literal(
                "Submitting sequence playback request: " + sequenceId
        ).withStyle(ChatFormatting.GRAY), false);

        playAnnouncementRequest(source, AnnouncementPlaybackRequest.sequenceOnly(sequenceId));
        return 1;
    }

    private static void playAnnouncementRequest(
            CommandSourceStack source,
            AnnouncementPlaybackRequest request
    ) {
        ClientAnnouncementServices.runtime().play(request).whenComplete((result, throwable) -> {
            if (throwable != null) {
                source.sendFailure(Component.literal(
                        "Announcement playback failed: " + throwable.getMessage()
                ));
                return;
            }

            if (result == null) {
                source.sendFailure(Component.literal("Announcement playback failed: empty result."));
                return;
            }

            if (!result.accepted()) {
                PlaybackSession previous = result.previous();

                if (previous != null) {
                    source.sendSuccess(() -> Component.literal(
                            "Announcement rejected: active sequence has higher priority. "
                                    + "active="
                                    + previous.sequence().id()
                                    + ", activePriority="
                                    + previous.sequence().priority()
                                    + ", requested="
                                    + request.sequenceId()
                    ).withStyle(ChatFormatting.YELLOW), false);
                } else {
                    source.sendFailure(Component.literal(
                            "Announcement rejected: " + result.message()
                    ));
                }

                return;
            }

            PlaybackSession session = result.session();

            String action = switch (result.decision()) {
                case STARTED -> "started";
                case REPLACED -> "replaced previous session";
                case REJECTED_LOWER_PRIORITY -> "rejected";
            };

            source.sendSuccess(() -> Component.literal(
                    "Announcement playback "
                            + action
                            + ": "
                            + session.id()
                            + ", channel="
                            + session.channel()
                            + ", sequence="
                            + session.sequence().id()
                            + ", priority="
                            + session.sequence().priority()
            ).withStyle(ChatFormatting.GREEN), false);
        });
    }

    private static int stopPlayback(CommandSourceStack source) {
        ClientAnnouncementRuntime.playbackManager().stopAll();

        source.sendSuccess(() -> Component.literal(
                "Stopped all playback sessions."
        ).withStyle(ChatFormatting.YELLOW), false);

        return 1;
    }

    private static int playbackStatus(CommandSourceStack source) {
        Map<AudioChannel, PlaybackSession> sessions = ClientAnnouncementRuntime.playbackManager().sessions();

        if (sessions.isEmpty()) {
            source.sendSuccess(() -> Component.literal(
                    "No active playback sessions."
            ).withStyle(ChatFormatting.GRAY), false);
            return 1;
        }

        source.sendSuccess(() -> Component.literal(
                "Active playback sessions: " + sessions.size()
        ).withStyle(ChatFormatting.GREEN), false);

        for (Map.Entry<AudioChannel, PlaybackSession> entry : sessions.entrySet()) {
            AudioChannel channel = entry.getKey();
            PlaybackSession session = entry.getValue();

            source.sendSuccess(() -> Component.literal(
                    channel
                            + " -> "
                            + session.sequence().id()
                            + ", priority="
                            + session.sequence().priority()
                            + ", state="
                            + session.state()
                            + ", id="
                            + session.id()
            ).withStyle(ChatFormatting.GRAY), false);
        }

        return 1;
    }

    private static SequenceAudioBackend createLocalOggBackend(CommandSourceStack source) {
        try {
            Class<?> clazz = Class.forName(
                    "com.misaka10843.createrailwayannouncer.client.audio.LocalOggSequenceAudioBackend"
            );

            Object instance = clazz
                    .getConstructor(CommandSourceStack.class)
                    .newInstance(source);

            return (SequenceAudioBackend) instance;
        } catch (Throwable t) {
            CreateRailwayAnnouncer.LOGGER.error("Failed to create local OGG playback backend", t);
            return null;
        }
    }

    private static int dryPlaySequence(CommandSourceStack source, String sequenceId) {
        SequenceTemplate template = VoicePackManager.sequences().get(sequenceId).orElse(null);

        if (template == null) {
            source.sendFailure(Component.literal("Sequence not found: " + sequenceId + ". Use /cra pack reload first."));
            return 0;
        }

        source.sendSuccess(() -> Component.literal(
                "Resolving sequence for dry playback: " + template.id()
        ).withStyle(ChatFormatting.GRAY), false);

        sequenceResolver().resolveSequence(template).thenAccept(sequence -> {
            PLAYBACK_SCHEDULER.playDry(sequence, new LoggingSequenceAudioBackend(source));
        });

        return 1;
    }

    private static int printSequence(CommandSourceStack source, String sequenceId) {
        SequenceTemplate template = VoicePackManager.sequences().get(sequenceId).orElse(null);

        if (template == null) {
            source.sendFailure(Component.literal("Sequence not found: " + sequenceId + ". Use /cra pack reload first."));
            return 0;
        }

        source.sendSuccess(() -> Component.literal(
                "Resolving playback queue: " + template.id()
        ).withStyle(ChatFormatting.GRAY), false);

        sequenceResolver().resolveSequence(template).thenAccept(sequence -> {
            printResolvedSequence(source, sequence);
        });

        return 1;
    }

    private static void printResolvedSequence(CommandSourceStack source, ResolvedSequence sequence) {
        source.sendSuccess(() -> Component.literal(
                "Resolved sequence: " + sequence.id()
                        + ", channel=" + sequence.channel()
                        + ", priority=" + sequence.priority()
                        + ", items=" + sequence.items().size()
        ).withStyle(ChatFormatting.GREEN), false);

        int maxLines = Math.min(sequence.items().size(), 20);

        for (int i = 0; i < maxLines; i++) {
            ResolvedSequenceItem item = sequence.items().get(i);
            int index = i;

            switch (item.type()) {
                case AUDIO -> source.sendSuccess(() -> Component.literal(
                        "[" + index + "] AUDIO " + item.audioPath()
                ).withStyle(ChatFormatting.GRAY), false);

                case SOUND -> source.sendSuccess(() -> Component.literal(
                        "[" + index + "] SOUND " + item.audioPath()
                ).withStyle(ChatFormatting.GRAY), false);

                case PAUSE -> source.sendSuccess(() -> Component.literal(
                        "[" + index + "] PAUSE " + item.pauseMs() + "ms"
                ).withStyle(ChatFormatting.GRAY), false);

                case SUBTITLE -> source.sendSuccess(() -> Component.literal(
                        "[" + index + "] SUBTITLE " + item.text()
                ).withStyle(ChatFormatting.GRAY), false);
            }
        }

        if (sequence.items().size() > maxLines) {
            int remaining = sequence.items().size() - maxLines;
            source.sendSuccess(() -> Component.literal(
                    "... " + remaining + " more items"
            ).withStyle(ChatFormatting.DARK_GRAY), false);
        }
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
                    "Loaded voice pack: " + pack.name() + " (" + pack.id() + "), phrases=" + VoicePackManager.phrases().size() + ", Sequences=" + VoicePackManager.sequences().size()
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