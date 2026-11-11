package com.misaka10843.create_railway_announcer.client.audio;

import com.misaka10843.create_railway_announcer.CreateRailwayAnnouncer;
import com.misaka10843.create_railway_announcer.playback.PlaybackState;
import com.misaka10843.create_railway_announcer.playback.SequenceAudioBackend;
import com.misaka10843.create_railway_announcer.sequence.ResolvedSequence;
import com.misaka10843.create_railway_announcer.sequence.ResolvedSequenceItem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public final class LocalOggSequenceAudioBackend implements SequenceAudioBackend {
    private static final int SESSION_FADE_IN_MS = 250;
    private static final int SESSION_FADE_OUT_MS = 500;
    private static final int ITEM_FADE_IN_MS = 0;

    private final LocalOggAudioPlayer player = new LocalOggAudioPlayer();
    private final AtomicBoolean firstPlayableItem = new AtomicBoolean(true);

    public LocalOggSequenceAudioBackend() {
    }
    public LocalOggSequenceAudioBackend scoped(net.minecraft.core.BlockPos position, int range, boolean onboard) {
        var backend = new LocalOggSequenceAudioBackend();
        if (!onboard) backend.player.setPosition(position,range);
        return backend;
    }

    public CompletableFuture<Void> preview(java.nio.file.Path path) {
        player.stopAll();
        return player.play(path, ClientAudioSettings.gainFor(com.misaka10843.create_railway_announcer.audio.AudioChannel.ONBOARD_VOICE), 150);
    }

    @Override
    public String id() {
        return "local_ogg";
    }

    @Override
    public void onSequenceStart(ResolvedSequence sequence) {
        firstPlayableItem.set(true);

        CreateRailwayAnnouncer.LOGGER.info(
                "Local OGG playback started: {}, channel={}, priority={}, items={}, playableItems={}, totalDurationMs={}",
                sequence.id(),
                sequence.channel(),
                sequence.priority(),
                sequence.items().size(),
                sequence.playableItemCount(),
                sequence.totalDurationMs()
        );
    }

    @Override
    public void onSequenceEnd(ResolvedSequence sequence, PlaybackState state) {
        CreateRailwayAnnouncer.LOGGER.info(
                "Local OGG playback ended: {}, state={}",
                sequence.id(),
                state
        );
    }

    @Override
    public CompletableFuture<Void> playAudio(ResolvedSequence sequence, ResolvedSequenceItem item) {
        CreateRailwayAnnouncer.LOGGER.info(
                "[{}] PLAY AUDIO {} durationMs={} startOffsetMs={}",
                sequence.id(),
                item.audioPath(),
                item.durationMs(),
                item.startOffsetMs()
        );

        int fadeInMs = firstPlayableItem.getAndSet(false)
                ? SESSION_FADE_IN_MS
                : ITEM_FADE_IN_MS;

        float gain = gainFor(sequence);

        return player.play(item.audioPath(), gain, fadeInMs, item.startOffsetMs()).whenComplete((ignored, throwable) -> {
            if (throwable != null) {
                CreateRailwayAnnouncer.LOGGER.error(
                        "Local OGG audio playback failed: {}",
                        item.audioPath(),
                        throwable
                );
            }
        });
    }

    @Override
    public CompletableFuture<Void> playSound(ResolvedSequence sequence, ResolvedSequenceItem item) {
        CreateRailwayAnnouncer.LOGGER.info(
                "[{}] PLAY SOUND {} durationMs={} startOffsetMs={}",
                sequence.id(),
                item.audioPath(),
                item.durationMs(),
                item.startOffsetMs()
        );

        int fadeInMs = sequence.channel() == com.misaka10843.create_railway_announcer.audio.AudioChannel.DOOR_CHIME ? 0 : firstPlayableItem.getAndSet(false)
                ? SESSION_FADE_IN_MS
                : ITEM_FADE_IN_MS;

        float gain = gainFor(sequence);

        return player.play(item.audioPath(), gain, fadeInMs, item.startOffsetMs()).whenComplete((ignored, throwable) -> {
            if (throwable != null) {
                CreateRailwayAnnouncer.LOGGER.error(
                        "Local OGG sound playback failed: {}",
                        item.audioPath(),
                        throwable
                );
            }
        });
    }

    @Override
    public void showSubtitle(ResolvedSequence sequence, ResolvedSequenceItem item) {
        if (!com.misaka10843.create_railway_announcer.config.ClientConfig.SUBTITLES_ENABLED.get()) return;
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
        float gain = ClientAudioSettings.gainFor(sequence.channel());
        var player = Minecraft.getInstance().player;
        if (sequence.channel() == com.misaka10843.create_railway_announcer.audio.AudioChannel.PLATFORM_VOICE
                && player != null && com.misaka10843.create_railway_announcer.compat.create.CreateTrainAdapterHolder.get().ridingTrainId(player).isPresent()) {
            gain *= com.misaka10843.create_railway_announcer.config.ClientConfig.PLATFORM_VOLUME_WHEN_ON_TRAIN.get().floatValue();
        }
        return gain;
    }
}
