package com.misaka10843.create_railway_announcer.data;

import com.misaka10843.create_railway_announcer.model.StationAnnouncementSettings;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

public final class AnnouncementSavedData extends SavedData {
    private boolean enabled=true;
    private final Map<String,StationAnnouncementSettings> stations=new LinkedHashMap<>();
    private long revision;
    private long sent=-1;
    public static AnnouncementSavedData get(MinecraftServer server){return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(AnnouncementSavedData::new,AnnouncementSavedData::load),"create_railway_announcer");}
    public boolean enabled(){return enabled;}
    public long revision(){return revision;}
    public Map<String,StationAnnouncementSettings> stations(){return Map.copyOf(stations);}
    public StationAnnouncementSettings station(String id){return stations.getOrDefault(id,StationAnnouncementSettings.DEFAULT);}
    public void set(String id,StationAnnouncementSettings settings,Boolean global){
        stations.put(id,settings);if(global!=null)enabled=global;revision++;setDirty();
    }
    public CompoundTag snapshot(){return save(new CompoundTag(),null);}
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider lookup){
        tag.putBoolean("Enabled",enabled);tag.putLong("Revision",revision);
        var profiles=new CompoundTag();stations.forEach((id,value)->profiles.put(id,value.save()));tag.put("Stations",profiles);return tag;
    }
    public static AnnouncementSavedData load(CompoundTag tag,HolderLookup.Provider lookup){
        var data=new AnnouncementSavedData();data.enabled=!tag.contains("Enabled")||tag.getBoolean("Enabled");data.revision=tag.getLong("Revision");
        var profiles=tag.getCompound("Stations");for(String id:profiles.getAllKeys())data.stations.put(id,StationAnnouncementSettings.load(profiles.getCompound(id)));return data;
    }
    public static void syncIfChanged(MinecraftServer server){
        var data=get(server);if(data.sent==data.revision)return;data.sent=data.revision;
        net.neoforged.neoforge.network.PacketDistributor.sendToAllPlayers(new com.misaka10843.create_railway_announcer.network.AnnouncementDataPayload(data.snapshot()));
    }
}
