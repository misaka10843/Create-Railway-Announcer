package com.misaka10843.create_railway_announcer.compat.create;

import com.simibubi.create.content.trains.schedule.ScheduleRuntime;
import com.simibubi.create.content.trains.schedule.destination.DestinationInstruction;
import java.util.Optional;

public final class CreateScheduleResolver {
    private CreateScheduleResolver() {}
    public static Optional<CreateScheduleSnapshot> resolve(ScheduleRuntime runtime) {
        var schedule = runtime == null ? null : runtime.getSchedule();
        if (schedule == null || schedule.entries.isEmpty()) return Optional.empty();
        int start = runtime.currentEntry + (runtime.state == ScheduleRuntime.State.POST_TRANSIT ? 1 : 0);
        String next = "";
        String destination = "";
        for (int offset = 0; offset < schedule.entries.size(); offset++) {
            int index = start + offset;
            if (!schedule.cyclic && index >= schedule.entries.size()) break;
            index = Math.floorMod(index, schedule.entries.size());
            if (schedule.entries.get(index).instruction instanceof DestinationInstruction instruction) {
                if (next.isBlank()) next = instruction.getFilter();
                destination = instruction.getFilter();
            }
        }
        return Optional.of(new CreateScheduleSnapshot(next, destination, runtime.currentEntry));
    }
}
