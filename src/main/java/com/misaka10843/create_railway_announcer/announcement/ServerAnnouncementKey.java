package com.misaka10843.create_railway_announcer.announcement;

import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record ServerAnnouncementKey(
        ResourceLocation levelId,
        UUID trainId,
        String stationConfigId,
        AnnouncementEventType eventType
) {
}