package com.misaka10843.createrailwayannouncer.compat.create;

import com.misaka10843.createrailwayannouncer.CreateRailwayAnnouncer;
import com.simibubi.create.content.trains.schedule.ScheduleRuntime;
import com.simibubi.create.content.trains.schedule.ScheduleEntry;
import com.simibubi.create.content.trains.schedule.destination.DestinationInstruction;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class CreateScheduleResolver {

    private CreateScheduleResolver() {
    }


    public static Optional<CreateScheduleSnapshot> resolve(
            ScheduleRuntime runtime
    ) {

        if (runtime == null) {
            return Optional.empty();
        }

        try {

            Field scheduleField =
                    ScheduleRuntime.class.getDeclaredField(
                            "schedule"
                    );

            scheduleField.setAccessible(true);


            Object schedule =
                    scheduleField.get(runtime);


            if (schedule == null) {
                return Optional.empty();
            }


            Field entriesField =
                    schedule.getClass()
                            .getDeclaredField(
                                    "entries"
                            );

            entriesField.setAccessible(true);


            List<?> entries =
                    (List<?>) entriesField.get(schedule);


            Field currentEntryField =
                    ScheduleRuntime.class.getDeclaredField(
                            "currentEntry"
                    );

            currentEntryField.setAccessible(true);


            int currentEntry =
                    currentEntryField.getInt(runtime);


            List<String> destinations =
                    new ArrayList<>();


            for (Object entry : entries) {

                if (!(entry instanceof ScheduleEntry scheduleEntry)) {
                    continue;
                }

                if (scheduleEntry.instruction
                        instanceof DestinationInstruction destination) {

                    destinations.add(
                            destination.getFilter()
                    );
                }
            }

            /*CreateRailwayAnnouncer.LOGGER.debug(
                    "Create schedule debug: currentEntry={}, destinations={}",
                    currentEntry,
                    destinations
            );*/

            String next = "";

            String destination = "";


            if (!destinations.isEmpty()) {
                int nextIndex = currentEntry + 1;

                if (nextIndex >= destinations.size()) {
                    nextIndex = 0;
                }

                next = destinations.get(nextIndex);
            }
            /*CreateRailwayAnnouncer.LOGGER.debug(
                    "Create schedule result: currentEntry={}, next={}, destination={}",
                    currentEntry,
                    next,
                    destination
            );*/

            if (!destinations.isEmpty()) {
                destination = destinations.get(destinations.size() - 1);
            }


            return Optional.of(
                    new CreateScheduleSnapshot(
                            next,
                            destination,
                            currentEntry
                    )
            );


        } catch (Throwable ignored) {

            return Optional.empty();
        }
    }
}