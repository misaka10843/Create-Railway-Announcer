package com.misaka10843.createrailwayannouncer.compat.create;

import com.misaka10843.createrailwayannouncer.CreateRailwayAnnouncer;
import com.simibubi.create.Create;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.schedule.ScheduleRuntime;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

public final class Create6TrainAdapter implements CreateTrainAdapter {

    private static final String MANAGER =
            "com.simibubi.create.content.trains.GlobalRailwayManager";

    @Override
    public boolean isAvailable() {
        try {
            Class.forName(MANAGER);
            Class.forName(
                    "com.simibubi.create.content.trains.entity.Train"
            );

            return true;

        } catch (Throwable e) {
            return false;
        }
    }


    @Override
    public Optional<CreateTrainSnapshot> findTrain(UUID trainId) {

        if (trainId == null) {
            return Optional.empty();
        }

        try {

            Train train = trains().get(trainId);

            if (train == null) {
                return Optional.empty();
            }

            return Optional.of(snapshot(train));


        } catch (Throwable e) {

            CreateRailwayAnnouncer.LOGGER.error(
                    "Failed finding Create train {}",
                    trainId,
                    e
            );

            return Optional.empty();
        }
    }

    @Override
    public Optional<CreateTrainSnapshot> findTrainContainingClientPlayer(
            LocalPlayer player
    ) {
        if (player == null || player.getVehicle() == null) {
            return Optional.empty();
        }

        try {
            Object vehicle = player.getVehicle();

            for (Train train : trains().values()) {

                for (var carriage : train.carriages) {

                    String className =
                            carriage.getClass().getName();

                    if (className.contains("Carriage")) {

                        CreateRailwayAnnouncer.LOGGER.debug(
                                "Client vehicle match candidate: {}, train={}",
                                className,
                                train.id
                        );

                        return Optional.of(
                                snapshot(train)
                        );
                    }
                }
            }

        } catch (Throwable e) {

            CreateRailwayAnnouncer.LOGGER.warn(
                    "Failed detecting client train",
                    e
            );
        }

        return Optional.empty();
    }

    @Override
    public Optional<CreateTrainSnapshot> findTrainContainingServerPlayer(
            ServerPlayer player
    ) {

        if (player == null) {
            return Optional.empty();
        }


        try {

            Map<UUID, Train> trains = trains();


            for (Train train : trains.values()) {


                int count = train.countPlayerPassengers();


                if (count > 0) {
                    return Optional.of(snapshot(train));
                }
            }


        } catch (Throwable e) {

            CreateRailwayAnnouncer.LOGGER.error(
                    "Failed detecting player train",
                    e
            );
        }


        return Optional.empty();
    }


    @Override
    public Optional<CreateTrainSnapshot> findNearestTrain(
            ServerLevel level,
            double x,
            double y,
            double z,
            double maxDistance
    ) {

        try {

            Train nearest = null;
            double nearestDistance = Double.MAX_VALUE;


            for (Train train : trains().values()) {


                Optional<Vec3> position =
                        getPosition(
                                train,
                                level
                        );


                if (position.isEmpty()) {
                    continue;
                }


                double distance =
                        position.get()
                                .distanceTo(
                                        new Vec3(
                                                x,
                                                y,
                                                z
                                        )
                                );


                if (distance < nearestDistance
                        && distance <= maxDistance) {

                    nearestDistance = distance;
                    nearest = train;
                }
            }


            if (nearest != null) {
                return Optional.of(snapshot(nearest));
            }


        } catch (Throwable e) {

            CreateRailwayAnnouncer.LOGGER.error(
                    "Failed finding nearest Create train",
                    e
            );
        }


        return Optional.empty();
    }

    private Map<UUID, Train> trains() {
        return Create.RAILWAYS.trains;
    }

    public java.util.Collection<Train> allTrains() {
        return trains().values();
    }

    private Optional<Vec3> getPosition(
            Train train,
            ServerLevel level
    ) {

        try {

            Method method =
                    train.getClass()
                            .getMethod(
                                    "getPositionInDimension",
                                    net.minecraft.resources.ResourceKey.class
                            );


            return (Optional<Vec3>) method.invoke(
                    train,
                    level.dimension()
            );


        } catch (Throwable e) {

            return Optional.empty();
        }
    }


    public CreateTrainSnapshot snapshot(
            Train train
    ) {

        AtomicReference<CreateStationSnapshot> currentStation =
                new AtomicReference<>();


        try {

            Object station =
                    train.getCurrentStation();


            if (station != null) {

                CreateStationSnapshot rawStation =
                        CreateStationResolver.resolve(station)
                                .orElse(null);

                if (rawStation != null) {

                    CreateStationMatcher.match(
                            rawStation.name()
                    ).ifPresentOrElse(
                            config -> {

                                CreateRailwayAnnouncer.LOGGER.trace(
                                        "Matched current station: create={}, config={}",
                                        rawStation.name(),
                                        config.getCustomId()
                                );

                                currentStation.set(
                                        new CreateStationSnapshot(
                                                config.getCreateStationId() != null
                                                        ? config.getCreateStationId()
                                                        : UUID.nameUUIDFromBytes(
                                                        config.getCustomId()
                                                                .getBytes(java.nio.charset.StandardCharsets.UTF_8)
                                                ),
                                                config.getCustomId(),
                                                config.getPosition()
                                        )
                                );
                            },

                            () -> {

                                CreateRailwayAnnouncer.LOGGER.warn(
                                        "Failed matching current station: {}",
                                        rawStation.name()
                                );
                            }
                    );
                }
            }

        } catch (Throwable ignored) {
        }


        AtomicReference<CreateStationSnapshot> nextStation = new AtomicReference<>();
        AtomicReference<CreateStationSnapshot> destinationStation = new AtomicReference<>();

        try {
            Field runtimeField = Train.class.getDeclaredField("runtime");
            runtimeField.setAccessible(true);

            ScheduleRuntime runtime = (ScheduleRuntime) runtimeField.get(train);

            CreateScheduleSnapshot schedule =
                    CreateScheduleResolver.resolve(runtime)
                            .orElse(null);

            if (schedule != null) {
                if (!schedule.nextStationFilter().isBlank()) {
                    CreateStationMatcher.match(
                            schedule.nextStationFilter()
                    ).ifPresent(station -> {

                        nextStation.set(new CreateStationSnapshot(
                                station.getCreateStationId() != null
                                        ? station.getCreateStationId()
                                        : UUID.nameUUIDFromBytes(
                                        station.getCustomId()
                                                .getBytes(java.nio.charset.StandardCharsets.UTF_8)
                                ),
                                station.getCustomId(),
                                station.getPosition()
                        ));
                    });
                }

                if (!schedule.destinationFilter().isBlank()) {
                    CreateStationMatcher.match(
                            schedule.destinationFilter()
                    ).ifPresent(station -> {

                        destinationStation.set(new CreateStationSnapshot(
                                station.getCreateStationId() != null
                                        ? station.getCreateStationId()
                                        : UUID.nameUUIDFromBytes(
                                        station.getCustomId()
                                                .getBytes(java.nio.charset.StandardCharsets.UTF_8)
                                ),
                                station.getCustomId(),
                                station.getPosition()
                        ));
                    });
                }
            }
        } catch (Throwable ignored) {
        }


        return new CreateTrainSnapshot(
                train.id,
                train.name.getString(),
                BlockPos.ZERO,
                currentStation.get(),
                null,
                nextStation.get(),
                null,
                destinationStation.get(),
                train.speed != 0,
                train.speed
        );
    }
}