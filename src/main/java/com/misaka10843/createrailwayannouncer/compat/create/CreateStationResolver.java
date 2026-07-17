package com.misaka10843.createrailwayannouncer.compat.create;

import com.misaka10843.createrailwayannouncer.CreateRailwayAnnouncer;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

public final class CreateStationResolver {

    private static final String GLOBAL_STATION =
            "com.simibubi.create.content.trains.station.GlobalStation";


    private CreateStationResolver() {
    }


    public static boolean isAvailable() {
        try {
            Class.forName(GLOBAL_STATION);
            return true;
        } catch (Throwable e) {
            return false;
        }
    }


    public static Optional<CreateStationSnapshot> resolve(
            Object station
    ) {

        if (station == null) {
            return Optional.empty();
        }


        try {

            Class<?> clazz =
                    station.getClass();

            String name = "";




            /*
             * name
             */
            try {

                Field nameField =
                        clazz.getDeclaredField("name");

                nameField.setAccessible(true);

                Object value = nameField.get(station);

                if (value instanceof net.minecraft.network.chat.Component component) {
                    name = component.getString();
                } else if (value != null) {
                    name = value.toString();
                }


            } catch (Throwable ignored) {
            }

            UUID id =
                    UUID.nameUUIDFromBytes(
                            name.getBytes(StandardCharsets.UTF_8)
                    );
            CreateRailwayAnnouncer.LOGGER.trace(
                    "Resolved Create station: id={}, name={}",
                    id,
                    name
            );
            return Optional.of(
                    new CreateStationSnapshot(
                            id,
                            name,
                            null
                    )
            );


        } catch (Throwable t) {

            CreateRailwayAnnouncer.LOGGER.error(
                    "Failed resolving Create station",
                    t
            );

            return Optional.empty();
        }
    }
}