package com.misaka10843.createrailwayannouncer.compat.create;

import com.misaka10843.createrailwayannouncer.CreateRailwayAnnouncer;

import java.lang.reflect.Modifier;
import java.util.Arrays;

public final class CreateApiProbe {

    private CreateApiProbe() {
    }


    public static void probe() {
        CreateRailwayAnnouncer.LOGGER.info(
                "========== Create API Probe =========="
        );

        probeClass(
                "com.simibubi.create.content.trains.GlobalRailwayManager"
        );
        probeConstructors(
                "com.simibubi.create.content.trains.GlobalRailwayManager"
        );
        probeStaticFields(
                "com.simibubi.create.content.trains.GlobalRailwayManager"
        );
        probeClass(
                "com.simibubi.create.content.trains.entity.Train"
        );
        probeClass(
                "com.simibubi.create.content.trains.schedule.ScheduleRuntime"
        );

        probeClass(
                "com.simibubi.create.content.trains.schedule.Schedule"
        );

        probeClass(
                "com.simibubi.create.content.trains.management.edgePoint.station.GlobalStation"
        );

        probeClass(
                "com.simibubi.create.content.trains.management.edgePoint.station.GlobalStationData"
        );
        probeClass(
                "com.simibubi.create.content.trains.management.edgePoint.station.GlobalStation"
        );

        CreateRailwayAnnouncer.LOGGER.info(
                "========== Create API Probe Finished =========="
        );
    }
    private static void probeStaticFields(String className) {

        try {

            Class<?> clazz =
                    Class.forName(className);


            CreateRailwayAnnouncer.LOGGER.info(
                    "Static fields of {}:",
                    className
            );


            Arrays.stream(clazz.getDeclaredFields())
                    .filter(field ->
                            Modifier.isStatic(field.getModifiers())
                    )
                    .forEach(field ->
                            CreateRailwayAnnouncer.LOGGER.info(
                                    "  {} {}",
                                    field.getType().getName(),
                                    field.getName()
                            )
                    );


        } catch (Throwable e) {

            CreateRailwayAnnouncer.LOGGER.warn(
                    "Failed probing static fields",
                    e
            );
        }
    }
    private static void probeConstructors(String className) {
        try {
            Class<?> clazz = Class.forName(className);

            CreateRailwayAnnouncer.LOGGER.info(
                    "Constructors of {}:",
                    className
            );

            Arrays.stream(clazz.getDeclaredConstructors())
                    .forEach(constructor ->
                            CreateRailwayAnnouncer.LOGGER.info(
                                    "  {}",
                                    constructor
                            )
                    );


        } catch (Throwable e) {
            CreateRailwayAnnouncer.LOGGER.warn(
                    "Failed probing constructors: {}",
                    className,
                    e
            );
        }
    }
    private static void probeClass(String className) {
        try {
            Class<?> clazz = Class.forName(className);

            CreateRailwayAnnouncer.LOGGER.info(
                    "FOUND CLASS: {}",
                    className
            );


            CreateRailwayAnnouncer.LOGGER.info(
                    "Methods:"
            );

            Arrays.stream(clazz.getDeclaredMethods())
                    .sorted((a, b) ->
                            a.getName().compareTo(b.getName())
                    )
                    .forEach(method ->
                            CreateRailwayAnnouncer.LOGGER.info(
                                    "  {} {}({}) static={} public={}",
                                    method.getReturnType().getName(),
                                    method.getName(),
                                    Arrays.stream(method.getParameterTypes())
                                            .map(Class::getName)
                                            .reduce(
                                                    "",
                                                    (x, y) -> x.isEmpty()
                                                            ? y
                                                            : x + ", " + y
                                            ),
                                    java.lang.reflect.Modifier.isStatic(method.getModifiers()),
                                    java.lang.reflect.Modifier.isPublic(method.getModifiers())
                            )
                    );


            CreateRailwayAnnouncer.LOGGER.info(
                    "Fields:"
            );

            Arrays.stream(clazz.getDeclaredFields())
                    .sorted((a, b) ->
                            a.getName().compareTo(b.getName())
                    )
                    .forEach(field -> {

                        String extra = "";

                        if (field.getName().toLowerCase().contains("station")
                                || field.getName().toLowerCase().contains("schedule")
                                || field.getName().toLowerCase().contains("runtime")) {

                            extra = " <IMPORTANT>";
                        }

                        CreateRailwayAnnouncer.LOGGER.info(
                                "  {} {}{}",
                                field.getType().getSimpleName(),
                                field.getName(),
                                extra
                        );
                    });


        } catch (Throwable e) {

            CreateRailwayAnnouncer.LOGGER.warn(
                    "MISSING CLASS: {}",
                    className
            );
        }
    }
}