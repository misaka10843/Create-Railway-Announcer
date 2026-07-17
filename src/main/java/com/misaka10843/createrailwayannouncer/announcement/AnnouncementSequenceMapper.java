package com.misaka10843.createrailwayannouncer.announcement;

public final class AnnouncementSequenceMapper {

    private AnnouncementSequenceMapper() {
    }

    public static String sequenceId(
            AnnouncementEventType type
    ) {
        return switch (type) {
            case ONBOARD_NEXT_STOP ->
                    "onboard_next_stop_dynamic";

            case ONBOARD_ARRIVED ->
                    "onboard_arrived_dynamic";

            case ONBOARD_DEPARTING ->
                    "onboard_departing_dynamic";

            default ->
                    null;
        };
    }
}