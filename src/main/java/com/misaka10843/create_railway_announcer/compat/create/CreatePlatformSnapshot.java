package com.misaka10843.create_railway_announcer.compat.create;

import java.util.UUID;

public record CreatePlatformSnapshot(
        UUID platformId,
        String name,
        String lineId
) {
}