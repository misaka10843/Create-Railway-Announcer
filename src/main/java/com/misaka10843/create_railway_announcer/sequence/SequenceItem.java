package com.misaka10843.create_railway_announcer.sequence;

import com.google.gson.JsonObject;

public record SequenceItem(
        SequenceItemType type,
        JsonObject raw
) {
}