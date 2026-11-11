package com.misaka10843.create_railway_announcer.config;

import com.misaka10843.create_railway_announcer.model.StationAnnouncementSettings;
import net.minecraft.nbt.CompoundTag;
import java.util.*;

public final class AnnouncementClientData {
    private static boolean enabled=true;
    private static Map<String,StationAnnouncementSettings> profiles=Map.of();
    public static boolean enabled(){return enabled;}
    public static Map<String,StationAnnouncementSettings> profiles(){return profiles;}
    public static void clear(){enabled=true;profiles=Map.of();}
    public static void apply(CompoundTag tag){
        enabled=tag.getBoolean("Enabled");var next=new LinkedHashMap<String,StationAnnouncementSettings>();var source=tag.getCompound("Stations");
        for(String id:source.getAllKeys())next.put(id,StationAnnouncementSettings.load(source.getCompound(id)));profiles=Map.copyOf(next);
    }
}
