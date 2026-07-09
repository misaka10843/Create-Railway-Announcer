package com.misaka10843.createrailwayannouncer.client.audio;

import com.misaka10843.createrailwayannouncer.CreateRailwayAnnouncer;
import com.misaka10843.createrailwayannouncer.playback.PlaybackState;
import com.misaka10843.createrailwayannouncer.playback.SequenceAudioBackend;
import com.misaka10843.createrailwayannouncer.sequence.ResolvedSequence;
import com.misaka10843.createrailwayannouncer.sequence.ResolvedSequenceItem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public final class LocalOggSequenceAudioBackend implements SequenceAudioBackend {
    private static final int SESSION_FADE_IN_MS = 250;
    private static final int SESSION_FADE_OUT_MS = 500;
    private static final int ITEM_FADE_IN_MS = 0;

    private final LocalOggAudioPlayer player = new LocalOggAudioPlayer();
    private final CommandSourceStack source;
    private final AtomicBoolean firstPlayableItem = new AtomicBoolean(true);

    public LocalOggSequenceAudioBackend(CommandSourceStack source) {
        this.source = source;
    }

    @Override
    public String id() {
        return "local_ogg";
    }

    @Override
    public void onSequenceStart(ResolvedSequence sequence) {
        firstPlayableItem.set(true);

        String message = "Local OGG playback started: " + sequence.id()
                + ", channel=" + sequence.channel()
                + ", priority=" + sequence.priority()
                + ", items=" + sequence.items().size();

        CreateRailwayAnnouncer.LOGGER.info(message);
        source.sendSuccess(() -> Component.literal(message).withStyle(ChatFormatting.GREEN), false);
    }

    @Override
    public void onSequenceEnd(ResolvedSequence sequence, PlaybackState state) {
        String message = "Local OGG playback ended: " + sequence.id() + ", state=" + state;

        CreateRailwayAnnouncer.LOGGER.info(message);
        source.sendSuccess(() -> Component.literal(message).withStyle(ChatFormatting.GREEN), false);
    }

    @Override
    public CompletableFuture<Void> playAudio(ResolvedSequence sequence, ResolvedSequenceItem item) {
        CreateRailwayAnnouncer.LOGGER.info("[{}] PLAY AUDIO {}", sequence.id(), item.audioPath());

        int fadeInMs = firstPlayableItem.getAndSet(false)
                ? SESSION_FADE_IN_MS
                : ITEM_FADE_IN_MS;
        float gain = gainFor(sequence);

        return player.play(item.audioPath(), gain, fadeInMs).whenComplete((ignored, throwable) -> {
            if (throwable != null) {
                CreateRailwayAnnouncer.LOGGER.error("Local OGG audio playback failed: {}", item.audioPath(), throwable);
                source.sendFailure(Component.literal("Audio playback failed: " + throwable.getMessage()));
            }
        });
    }

    @Override
    public CompletableFuture<Void> playSound(ResolvedSequence sequence, ResolvedSequenceItem item) {
        CreateRailwayAnnouncer.LOGGER.info("[{}] PLAY SOUND {}", sequence.id(), item.audioPath());

        int fadeInMs = firstPlayableItem.getAndSet(false)
                ? SESSION_FADE_IN_MS
                : ITEM_FADE_IN_MS;

        float gain = gainFor(sequence);

        return player.play(item.audioPath(), gain, fadeInMs).whenComplete((ignored, throwable) -> {
            if (throwable != null) {
                CreateRailwayAnnouncer.LOGGER.error("Local OGG sound playback failed: {}", item.audioPath(), throwable);
                source.sendFailure(Component.literal("Sound playback failed: " + throwable.getMessage()));
            }
        });
    }

    @Override
    public void showSubtitle(ResolvedSequence sequence, ResolvedSequenceItem item) {
        String text = item.text();
        if (text == null || text.isBlank()) {
            return;
        }

        CreateRailwayAnnouncer.LOGGER.info("[{}] SUBTITLE {}", sequence.id(), text);

        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            if (minecraft.player != null) {
                minecraft.player.displayClientMessage(
                        Component.literal(text).withStyle(ChatFormatting.YELLOW),
                        true
                );
            }
        });
    }

    @Override
    public void stop() {
        player.fadeOutAndStopAll(SESSION_FADE_OUT_MS);
    }

    @Override
    public void fadeOutAndStop(int fadeOutMs) {
        player.fadeOutAndStopAll(fadeOutMs <= 0 ? SESSION_FADE_OUT_MS : fadeOutMs);
    }

    private float gainFor(ResolvedSequence sequence) {
        return ClientAudioSettings.gainFor(sequence.channel());
    }
}