package com.misaka10843.createrailwayannouncer.playback;

import com.misaka10843.createrailwayannouncer.CreateRailwayAnnouncer;
import com.misaka10843.createrailwayannouncer.sequence.ResolvedSequence;
import com.misaka10843.createrailwayannouncer.sequence.ResolvedSequenceItem;
import java.util.concurrent.CompletableFuture;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

public final class LoggingSequenceAudioBackend implements SequenceAudioBackend {
    private final CommandSourceStack source;

    public LoggingSequenceAudioBackend(CommandSourceStack source) {
        this.source = source;
    }

    @Override
    public String id() {
        return "logging";
    }

    @Override
    public void onSequenceStart(ResolvedSequence sequence) {
        String message = "Dry playback started: " + sequence.id()
                + ", channel=" + sequence.channel()
                + ", priority=" + sequence.priority()
                + ", items=" + sequence.items().size();

        CreateRailwayAnnouncer.LOGGER.info(message);
        source.sendSuccess(() -> Component.literal(message).withStyle(ChatFormatting.GREEN), false);
    }

    @Override
    public void onSequenceEnd(ResolvedSequence sequence, PlaybackState state) {
        String message = "Dry playback ended: " + sequence.id() + ", state=" + state;

        CreateRailwayAnnouncer.LOGGER.info(message);
        source.sendSuccess(() -> Component.literal(message).withStyle(ChatFormatting.GREEN), false);
    }

    @Override
    public CompletableFuture<Void> playAudio(ResolvedSequence sequence, ResolvedSequenceItem item) {
        String message = "DRY AUDIO: " + item.audioPath();

        CreateRailwayAnnouncer.LOGGER.info("[{}] {}", sequence.id(), message);
        source.sendSuccess(() -> Component.literal(message).withStyle(ChatFormatting.GRAY), false);

        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> playSound(ResolvedSequence sequence, ResolvedSequenceItem item) {
        String message = "DRY SOUND: " + item.audioPath();

        CreateRailwayAnnouncer.LOGGER.info("[{}] {}", sequence.id(), message);
        source.sendSuccess(() -> Component.literal(message).withStyle(ChatFormatting.GRAY), false);

        return CompletableFuture.completedFuture(null);
    }
    @Override
    public void showSubtitle(ResolvedSequence sequence, ResolvedSequenceItem item) {
        String message = "DRY SUBTITLE: " + item.text();

        CreateRailwayAnnouncer.LOGGER.info("[{}] {}", sequence.id(), message);
        source.sendSuccess(() -> Component.literal(message).withStyle(ChatFormatting.YELLOW), false);
    }
}