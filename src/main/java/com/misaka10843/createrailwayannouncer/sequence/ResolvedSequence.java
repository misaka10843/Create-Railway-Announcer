package com.misaka10843.createrailwayannouncer.sequence;

import com.misaka10843.createrailwayannouncer.CreateRailwayAnnouncer;
import com.misaka10843.createrailwayannouncer.audio.AudioChannel;

import java.util.ArrayList;
import java.util.List;

public record ResolvedSequence(
        String id,
        String event,
        AudioChannel channel,
        int priority,
        List<ResolvedSequenceItem> items
) {
    public long totalDurationMs() {
        long total = 0L;

        for (ResolvedSequenceItem item : items) {
            if (item == null) {
                continue;
            }

            total += Math.max(0L, item.durationMs());
        }

        return total;
    }

    public int playableItemCount() {
        int count = 0;

        for (ResolvedSequenceItem item : items) {
            if (item == null) {
                continue;
            }

            switch (item.type()) {
                case AUDIO, SOUND -> count++;
                case PAUSE, SUBTITLE -> {
                }
            }
        }

        return count;
    }

    public ResolvedSequence skipTo(long elapsedMs) {
        long safeElapsedMs = Math.max(0L, elapsedMs);

        if (safeElapsedMs <= 0L) {
            return this;
        }

        long originalTotalDurationMs = totalDurationMs();
        if (originalTotalDurationMs > 0L && safeElapsedMs >= originalTotalDurationMs) {
            CreateRailwayAnnouncer.LOGGER.info(
                    "Resolved sequence offset exhausted sequence: id={}, elapsedMs={}, totalDurationMs={}",
                    id,
                    safeElapsedMs,
                    originalTotalDurationMs
            );

            return new ResolvedSequence(
                    id,
                    event,
                    channel,
                    priority,
                    List.of()
            );
        }

        List<ResolvedSequenceItem> remainingItems = new ArrayList<>();
        long cursorMs = 0L;
        boolean started = false;

        for (ResolvedSequenceItem item : items) {
            if (item == null) {
                continue;
            }

            long itemDurationMs = Math.max(0L, item.durationMs());

            if (started) {
                remainingItems.add(item);
                continue;
            }

            if (item.type() == ResolvedSequenceItemType.SUBTITLE) {
                if (safeElapsedMs <= cursorMs) {
                    remainingItems.add(item);
                    started = true;
                }
                continue;
            }

            long itemStartMs = cursorMs;
            long itemEndMs = cursorMs + itemDurationMs;

            if (safeElapsedMs >= itemEndMs) {
                cursorMs = itemEndMs;
                continue;
            }

            long offsetInsideItemMs = Math.max(0L, safeElapsedMs - itemStartMs);

            switch (item.type()) {
                case PAUSE -> {
                    long remainingPauseMs = Math.max(0L, itemDurationMs - offsetInsideItemMs);
                    if (remainingPauseMs > 0L) {
                        remainingItems.add(item.withRemainingPause(remainingPauseMs));
                    }
                }
                case AUDIO, SOUND -> {
                    if (offsetInsideItemMs < itemDurationMs) {
                        remainingItems.add(item.withStartOffset(offsetInsideItemMs));
                    }
                }
                case SUBTITLE -> {
                }
            }

            started = true;
            cursorMs = itemEndMs;
        }

        CreateRailwayAnnouncer.LOGGER.info(
                "Resolved sequence offset applied: id={}, elapsedMs={}, originalItems={}, remainingItems={}, originalTotalDurationMs={}, remainingTotalDurationMs={}",
                id,
                safeElapsedMs,
                items.size(),
                remainingItems.size(),
                originalTotalDurationMs,
                new ResolvedSequence(id, event, channel, priority, remainingItems).totalDurationMs()
        );

        return new ResolvedSequence(
                id,
                event,
                channel,
                priority,
                List.copyOf(remainingItems)
        );
    }

    public boolean isEmpty() {
        return items == null || items.isEmpty();
    }
}