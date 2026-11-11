package com.misaka10843.create_railway_announcer.announcement;

import com.misaka10843.create_railway_announcer.compat.create.CreateTrainSnapshot;

import java.util.UUID;

public record TrainAnnouncementState(
        UUID trainId,
        CreateTrainSnapshot snapshot,
        boolean moving
) {
}