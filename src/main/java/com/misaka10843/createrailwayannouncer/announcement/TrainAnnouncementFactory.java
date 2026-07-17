package com.misaka10843.createrailwayannouncer.announcement;

import com.misaka10843.createrailwayannouncer.audio.AudioChannel;
import com.misaka10843.createrailwayannouncer.compat.create.CreateStationSnapshot;
import com.misaka10843.createrailwayannouncer.compat.create.CreateTrainSnapshot;
import net.minecraft.core.BlockPos;

import java.util.UUID;

public final class TrainAnnouncementFactory {
    private TrainAnnouncementFactory() {
    }

    public static AnnouncementEvent createNextStation(
            CreateTrainSnapshot train,
            BlockPos position,
            long gameTime
    ) {
        return new AnnouncementEvent(
                UUID.randomUUID(),
                AnnouncementEventType.ONBOARD_NEXT_STOP,
                train.trainId(),
                train.trainName(),
                stationId(train.nextStation()),
                stationPosition(train.nextStation(), position),
                stationConfigId(train.currentStation()),
                stationConfigId(train.nextStation()),
                stationConfigId(train.destinationStation()),
                AudioChannel.ONBOARD_VOICE,
                70,
                gameTime
        );
    }

    public static AnnouncementEvent createArrived(
            CreateTrainSnapshot train,
            BlockPos position,
            long gameTime
    ) {
        return new AnnouncementEvent(
                UUID.randomUUID(),
                AnnouncementEventType.ONBOARD_ARRIVED,
                train.trainId(),
                train.trainName(),
                stationId(train.currentStation()),
                stationPosition(train.currentStation(), position),
                stationConfigId(train.currentStation()),
                stationConfigId(train.nextStation()),
                stationConfigId(train.destinationStation()),
                AudioChannel.ONBOARD_VOICE,
                80,
                gameTime
        );
    }

    private static UUID stationId(CreateStationSnapshot station) {
        return station == null ? null : station.stationId();
    }

    private static String stationConfigId(CreateStationSnapshot station) {
        return station == null ? "" : station.name();
    }

    private static BlockPos stationPosition(
            CreateStationSnapshot station,
            BlockPos fallback
    ) {
        if (station != null && station.position() != null) {
            return station.position();
        }

        return fallback;
    }
}