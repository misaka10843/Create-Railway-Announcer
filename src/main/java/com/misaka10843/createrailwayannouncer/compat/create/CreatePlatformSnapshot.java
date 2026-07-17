package com.misaka10843.createrailwayannouncer.compat.create;

import java.util.UUID;

public record CreatePlatformSnapshot(
        UUID platformId,
        String name,
        String lineId
) {
}