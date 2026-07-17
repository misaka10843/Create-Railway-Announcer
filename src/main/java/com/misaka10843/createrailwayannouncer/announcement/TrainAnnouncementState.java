package com.misaka10843.createrailwayannouncer.announcement;

import com.misaka10843.createrailwayannouncer.compat.create.CreateTrainSnapshot;

import java.util.UUID;

public record TrainAnnouncementState(
        UUID trainId,
        CreateTrainSnapshot snapshot,
        boolean moving
) {
}