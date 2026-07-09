package com.misaka10843.createrailwayannouncer.sequence;

import com.misaka10843.createrailwayannouncer.audio.AudioChannel;

import java.util.List;

public record SequenceTemplate(
        String id,
        String event,
        AudioChannel channel,
        int priority,
        List<SequenceItem> items
) {
}