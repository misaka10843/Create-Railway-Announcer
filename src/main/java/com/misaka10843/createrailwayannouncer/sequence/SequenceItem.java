package com.misaka10843.createrailwayannouncer.sequence;

import com.google.gson.JsonObject;

public record SequenceItem(
        SequenceItemType type,
        JsonObject raw
) {
}