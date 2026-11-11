package com.misaka10843.create_railway_announcer.announcement;

public final class AnnouncementSequenceMapper {

    private AnnouncementSequenceMapper() {
    }
    public static com.misaka10843.create_railway_announcer.audio.AudioChannel channel(AnnouncementEventType type) {
        return switch(type) {
            case ONBOARD_NEXT_STOP,ONBOARD_APPROACHING,ONBOARD_ARRIVED,ONBOARD_DEPARTING,ONBOARD_TERMINAL,DOOR_OPENING,DOOR_CLOSING,SAFETY_NOTICE -> com.misaka10843.create_railway_announcer.audio.AudioChannel.ONBOARD_VOICE;
            case PLATFORM_APPROACH,PLATFORM_ARRIVAL,PLATFORM_PRE_DEPARTURE,PLATFORM_DOOR_CLOSING,PLATFORM_DEPARTED -> com.misaka10843.create_railway_announcer.audio.AudioChannel.PLATFORM_VOICE;
            case PLATFORM_DEPARTURE_MELODY -> com.misaka10843.create_railway_announcer.audio.AudioChannel.MELODY;
            case DOOR_OPEN_CHIME,DOOR_CLOSE_CHIME -> com.misaka10843.create_railway_announcer.audio.AudioChannel.DOOR_CHIME;
        };
    }
    public static java.util.List<AnnouncementEventType> manualEvents(){return java.util.Arrays.stream(AnnouncementEventType.values()).filter(e->channel(e)!=com.misaka10843.create_railway_announcer.audio.AudioChannel.DOOR_CHIME).toList();}
    public static java.util.List<AnnouncementEventType> speechEvents(com.misaka10843.create_railway_announcer.audio.AudioChannel channel){return manualEvents().stream().filter(e->channel(e)==channel).toList();}

    public static String sequenceId(
            AnnouncementEventType type
    ) {
        return switch (type) {
            case ONBOARD_NEXT_STOP ->
                    "onboard_next_stop_dynamic";

            case ONBOARD_ARRIVED ->
                    "onboard_arrived_dynamic";

            case DOOR_CLOSING -> "onboard_door_closing_dynamic";
            case ONBOARD_APPROACHING -> "onboard_approaching_dynamic";
            case ONBOARD_DEPARTING -> "onboard_departing_dynamic";
            case ONBOARD_TERMINAL -> "onboard_terminal_dynamic";
            case DOOR_OPENING -> "onboard_door_opening_dynamic";
            case SAFETY_NOTICE -> "onboard_safety_dynamic";
            case PLATFORM_APPROACH -> "platform_approach_dynamic";
            case PLATFORM_ARRIVAL -> "platform_arrival_dynamic";
            case PLATFORM_PRE_DEPARTURE -> "platform_pre_departure_dynamic";
            case PLATFORM_DOOR_CLOSING -> "platform_door_closing_dynamic";
            case PLATFORM_DEPARTED -> "platform_departed_dynamic";
            case PLATFORM_DEPARTURE_MELODY -> "platform_melody_dynamic";
            case DOOR_OPEN_CHIME -> "door_open_chime";
            case DOOR_CLOSE_CHIME -> "door_close_chime";
        };
    }
}
