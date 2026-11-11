package com.misaka10843.create_railway_announcer.sequence;

import com.misaka10843.create_railway_announcer.audio.AudioChannel;

import java.util.List;

public record SequenceTemplate(
        String id,
        String event,
        AudioChannel channel,
        int priority,
        List<SequenceItem> items
) {
}