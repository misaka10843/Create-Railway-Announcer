package com.misaka10843.create_railway_announcer.compat.create;

import com.misaka10843.create_railway_announcer.announcement.AnnouncementEventType;
import com.misaka10843.create_railway_announcer.audio.AudioChannel;
import com.misaka10843.create_railway_announcer.config.ServerConfig;
import com.misaka10843.create_railway_announcer.network.AnnouncementPacket;
import com.misaka10843.create_railway_announcer.data.AnnouncementSavedData;
import com.simibubi.create.content.decoration.slidingDoor.SlidingDoorBlock;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import com.simibubi.create.content.trains.entity.Train;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

public final class CreateDoorChimes {
    private static final Map<Train, DoorChimeGate> GATES = new WeakHashMap<>();
    private CreateDoorChimes() {}
    public static void changed(CarriageContraptionEntity entity, BlockPos localPos, StructureBlockInfo updated) {
        if (!(entity.level() instanceof ServerLevel level) || entity.getContraption() == null) return;
        var old = entity.getContraption().getBlocks().get(localPos);
        if (old == null || !(old.state().getBlock() instanceof SlidingDoorBlock)
                || !updated.state().is(old.state().getBlock()) || old.state().getValue(DoorBlock.HALF) != DoubleBlockHalf.LOWER
                || old.state().getValue(DoorBlock.OPEN).equals(updated.state().getValue(DoorBlock.OPEN))) return;
        var carriage = entity.getCarriage();
        if (carriage == null || carriage.train == null
                || !AnnouncementSavedData.get(level.getServer()).enabled()
                || !Create6TrainAdapter.hasOnboardSpeaker(carriage.train.id)) return;
        boolean opening = updated.state().getValue(DoorBlock.OPEN);
        var batch = GATES.computeIfAbsent(carriage.train, ignored -> new DoorChimeGate()).batch(opening, level.getGameTime());
        if(batch.claimAnnouncement()){
            var snapshot=new Create6TrainAdapter().snapshot(carriage.train);
            var station=snapshot.currentStation();
            if(station==null){var previous=com.misaka10843.create_railway_announcer.announcement.TrainSnapshotCache.get(carriage.train.id);if(previous!=null)station=previous.currentStation();}
            if(station!=null){
                var event=opening?AnnouncementEventType.DOOR_OPENING:AnnouncementEventType.DOOR_CLOSING;
                com.misaka10843.create_railway_announcer.announcement.TrainAnnouncementController.onboardEvent(level,snapshot,station,event);
                if(!opening){
                    String stationId=station.name();
                    com.misaka10843.create_railway_announcer.announcement.ServerActiveAnnouncementRegistry.stop(stationId,AudioChannel.MELODY);
                    com.misaka10843.create_railway_announcer.announcement.ServerAnnouncementDispatcher.dispatchToNearbyPlayers(level,
                            com.misaka10843.create_railway_announcer.announcement.ServerAnnouncementRequest.of(AnnouncementEventType.PLATFORM_DOOR_CLOSING,carriage.train.id,carriage.train.name.getString(),station.stationId(),station.position(),stationId,stationId,stationId,AudioChannel.PLATFORM_VOICE,90));
                }
            }
        }
        if(!ServerConfig.ENABLE_DOOR_CHIMES.get())return;
        var pos = BlockPos.containing(entity.toGlobalVector(Vec3.atCenterOf(localPos), 1));
        var event = opening ? AnnouncementEventType.DOOR_OPEN_CHIME : AnnouncementEventType.DOOR_CLOSE_CHIME;
        var packet = new AnnouncementPacket(batch.id(),event,opening ? "door_open_chime" : "door_close_chime",
                carriage.train.id,carriage.train.name.getString(),new UUID(0,0),pos,"","","",24,8,AudioChannel.DOOR_CHIME,100,
                level.getGameTime(),level.getGameTime(),false);
        for (var player : level.players()) {
            boolean riding = CreateTrainAdapterHolder.get().ridingTrainId(player).filter(carriage.train.id::equals).isPresent();
            if ((riding || Math.abs(player.getX()-pos.getX()) <= 24 && Math.abs(player.getY()-pos.getY()) <= 8 && Math.abs(player.getZ()-pos.getZ()) <= 24)
                    && batch.claim(player.getUUID()))
                PacketDistributor.sendToPlayer(player,packet);
        }
    }
}
