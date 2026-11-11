package com.misaka10843.create_railway_announcer.model;

import com.misaka10843.create_railway_announcer.config.DoorSide;
import com.misaka10843.create_railway_announcer.tts.Ssml;
import net.minecraft.nbt.CompoundTag;
import java.util.*;

public record StationAnnouncementSettings(boolean enabled, String reading, String voice, double speed, String pitch,
                                          String nextStopSsml, String arrivalSsml, String closingSsml, Map<String, DoorSide> doors,
                                          String platformVoice, double platformSpeed, String platformPitch,
                                          String platformApproachSsml, String platformArrivalSsml, String platformClosingSsml,Map<String,String> eventSsml) {
    public StationAnnouncementSettings(boolean enabled,String reading,String voice,double speed,String pitch,String nextStopSsml,String arrivalSsml,String closingSsml,Map<String,DoorSide> doors,
                                       String platformVoice,double platformSpeed,String platformPitch,String platformApproachSsml,String platformArrivalSsml,String platformClosingSsml){
        this(enabled,reading,voice,speed,pitch,nextStopSsml,arrivalSsml,closingSsml,doors,platformVoice,platformSpeed,platformPitch,platformApproachSsml,platformArrivalSsml,platformClosingSsml,Map.of());
    }
    public StationAnnouncementSettings(boolean enabled, String reading, String voice, double speed, String pitch,
                                       String nextStopSsml, String arrivalSsml, String closingSsml, Map<String, DoorSide> doors) {
        this(enabled, reading, voice, speed, pitch, nextStopSsml, arrivalSsml, closingSsml, doors, "", 0, "", "", "", "");
    }
    public static final StationAnnouncementSettings DEFAULT = new StationAnnouncementSettings(true, "", "", 0, "", "", "", "", Map.of());
    public StationAnnouncementSettings {
        reading = bounded(reading, 256);
        voice = bounded(voice, 128);
        pitch = bounded(pitch, 32);
        if (!Double.isFinite(speed) || speed != 0 && (speed < .5 || speed > 2)) throw new IllegalArgumentException("Speed must be 0 or 0.5..2.0");
        nextStopSsml = ssml(nextStopSsml);
        arrivalSsml = ssml(arrivalSsml);
        closingSsml = ssml(closingSsml);
        platformVoice = bounded(platformVoice,128);
        platformPitch = bounded(platformPitch,32);
        if (!Double.isFinite(platformSpeed) || platformSpeed != 0 && (platformSpeed < .5 || platformSpeed > 2)) throw new IllegalArgumentException("Speed must be 0 or 0.5..2.0");
        platformApproachSsml = ssml(platformApproachSsml);
        platformArrivalSsml = ssml(platformArrivalSsml);
        platformClosingSsml = ssml(platformClosingSsml);
        var extra=new LinkedHashMap<String,String>();
        if(eventSsml.size()>16)throw new IllegalArgumentException("Too many event settings");
        eventSsml.forEach((event,text)->{
            var type=com.misaka10843.create_railway_announcer.announcement.AnnouncementEventType.valueOf(event.toUpperCase(Locale.ROOT));
            if(type.name().endsWith("CHIME")||type==com.misaka10843.create_railway_announcer.announcement.AnnouncementEventType.PLATFORM_DEPARTURE_MELODY)throw new IllegalArgumentException("Sound events do not accept SSML");
            String value=ssml(text);if(!value.isBlank())extra.put(type.name().toLowerCase(Locale.ROOT),value);
        });eventSsml=Collections.unmodifiableMap(extra);
        if (doors.size() > 256) throw new IllegalArgumentException("Too many platform settings");
        Map<String, DoorSide> normalized = new LinkedHashMap<>();
        doors.forEach((id, side) -> { Objects.requireNonNull(side); if (side != DoorSide.NONE) normalized.put(bounded(id, 128).toLowerCase(Locale.ROOT), side); });
        doors = Collections.unmodifiableMap(normalized);
    }
    private static String bounded(String value, int limit) {
        value = value == null ? "" : value.trim();
        if (value.length() > limit) throw new IllegalArgumentException("Announcement field is too long");
        return value;
    }
    private static String ssml(String value) {
        value = bounded(value, 8192);
        if (!value.isEmpty()) {
            if (value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 8192) throw new IllegalArgumentException("SSML exceeds 8 KiB");
            Ssml.validate(value);
        }
        return value;
    }
    public DoorSide door(String platformId) { return doors.getOrDefault(platformId.toLowerCase(Locale.ROOT), DoorSide.NONE); }
    public StationAnnouncementSettings withReadingAndDoor(String value, String platformId, DoorSide side) {
        var changed = new LinkedHashMap<>(doors);
        changed.put(platformId.toLowerCase(Locale.ROOT), side);
        return new StationAnnouncementSettings(enabled, value, voice, speed, pitch, nextStopSsml, arrivalSsml, closingSsml, changed,
                platformVoice,platformSpeed,platformPitch,platformApproachSsml,platformArrivalSsml,platformClosingSsml,eventSsml);
    }
    public StationAnnouncementSettings withoutPlatform(String platformId) {
        var changed = new LinkedHashMap<>(doors);
        changed.remove(platformId.toLowerCase(Locale.ROOT));
        return new StationAnnouncementSettings(enabled, reading, voice, speed, pitch, nextStopSsml, arrivalSsml, closingSsml, changed,
                platformVoice,platformSpeed,platformPitch,platformApproachSsml,platformArrivalSsml,platformClosingSsml,eventSsml);
    }
    public CompoundTag save() {
        var tag = new CompoundTag();
        tag.putBoolean("Enabled", enabled); tag.putString("Reading", reading); tag.putString("Voice", voice);
        tag.putDouble("Speed", speed); tag.putString("Pitch", pitch);
        tag.putString("NextStopSsml", nextStopSsml); tag.putString("ArrivalSsml", arrivalSsml); tag.putString("ClosingSsml", closingSsml);
        tag.putString("PlatformVoice",platformVoice); tag.putDouble("PlatformSpeed",platformSpeed); tag.putString("PlatformPitch",platformPitch);
        tag.putString("PlatformApproachSsml",platformApproachSsml); tag.putString("PlatformArrivalSsml",platformArrivalSsml); tag.putString("PlatformClosingSsml",platformClosingSsml);
        var sides = new CompoundTag(); doors.forEach((id, side) -> sides.putString(id, side.name())); tag.put("Doors", sides);
        var events=new CompoundTag();eventSsml.forEach(events::putString);tag.put("EventSsml",events);
        return tag;
    }
    public static StationAnnouncementSettings load(CompoundTag tag) {
        if (tag.isEmpty()) return DEFAULT;
        Map<String, DoorSide> doors = new LinkedHashMap<>();
        var sides = tag.getCompound("Doors");
        for (String id : sides.getAllKeys()) doors.put(id, DoorSide.valueOf(sides.getString(id)));
        var eventTag=tag.getCompound("EventSsml");var events=new LinkedHashMap<String,String>();for(String event:eventTag.getAllKeys())events.put(event,eventTag.getString(event));
        return new StationAnnouncementSettings(tag.getBoolean("Enabled"), tag.getString("Reading"), tag.getString("Voice"),
                tag.getDouble("Speed"), tag.getString("Pitch"), tag.getString("NextStopSsml"), tag.getString("ArrivalSsml"), tag.getString("ClosingSsml"), doors,
                tag.getString("PlatformVoice"),tag.getDouble("PlatformSpeed"),tag.getString("PlatformPitch"),tag.getString("PlatformApproachSsml"),tag.getString("PlatformArrivalSsml"),tag.getString("PlatformClosingSsml"),events);
    }
}
