package com.misaka10843.create_railway_announcer.compat.create;

import com.misaka10843.create_railway_announcer.config.StationLineConfigStore;
import com.simibubi.create.Create;
import com.simibubi.create.CreateClient;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import java.util.*;

public final class Create6TrainAdapter implements CreateTrainAdapter {
    private static final Map<com.simibubi.create.content.contraptions.Contraption, Boolean> SPEAKERS = Collections.synchronizedMap(new WeakHashMap<>());
    public static boolean hasOnboardSpeaker(UUID id) {
        var train = Create.RAILWAYS.trains.get(id);
        if (train == null) return false;
        for (var carriage : train.carriages) {
            var entity = carriage.anyAvailableEntity();
            if (entity == null || entity.getContraption() == null) continue;
            if (SPEAKERS.computeIfAbsent(entity.getContraption(), contraption -> contraption.getBlocks().values().stream()
                    .anyMatch(block -> block.state().is(com.misaka10843.create_railway_announcer.registry.ModBlocks.ONBOARD_SPEAKER.get())
                            && block.state().getValue(com.misaka10843.create_railway_announcer.block.RailwaySpeakerBlock.ENABLED)))) return true;
        }
        return false;
    }
    @Override public boolean isAvailable() { return true; }
    public Collection<Train> allTrains() { return Create.RAILWAYS.trains.values(); }
    @Override public Optional<CreateTrainSnapshot> findTrain(UUID id) {
        return Optional.ofNullable(Create.RAILWAYS.trains.get(id)).map(this::snapshot);
    }
    private static UUID ridingTrain(Entity player) {
        Entity vehicle = player.getRootVehicle();
        return vehicle instanceof CarriageContraptionEntity carriage ? carriage.trainId : null;
    }
    @Override public Optional<UUID> ridingTrainId(Player player) {
        return player == null ? Optional.empty() : Optional.ofNullable(ridingTrain(player));
    }
    @Override public Optional<CreateTrainSnapshot> findTrainContainingClientPlayer(Player player) {
        if (player == null) return Optional.empty();
        return ClientLookup.find(player, this);
    }
    private static final class ClientLookup {
        private static Optional<CreateTrainSnapshot> find(Player player, Create6TrainAdapter adapter) {
            return Optional.ofNullable(CreateClient.RAILWAYS.trains.get(ridingTrain(player))).map(adapter::snapshot);
        }
    }
    @Override public Optional<CreateTrainSnapshot> findTrainContainingServerPlayer(ServerPlayer player) {
        return player == null ? Optional.empty() : findTrain(ridingTrain(player));
    }
    public Optional<BlockPos> position(Train train, ServerLevel level) {
        return train.carriages.stream().map(carriage -> carriage.getPositionInDimension(level.dimension()))
                .flatMap(Optional::stream).findFirst();
    }
    @Override public Optional<CreateTrainSnapshot> findNearestTrain(ServerLevel level, double x, double y, double z, double range) {
        Vec3 target = new Vec3(x, y, z);
        return allTrains().stream().filter(train -> position(train, level)
                .map(pos -> Vec3.atCenterOf(pos).distanceTo(target) <= range).orElse(false))
                .min(Comparator.comparingDouble(train -> Vec3.atCenterOf(position(train, level).orElseThrow()).distanceTo(target)))
                .map(this::snapshot);
    }
    public CreateTrainSnapshot snapshot(Train train) {
        CreateStationSnapshot current = null;
        var station = train.getCurrentStation();
        if (station != null) {
            current = StationLineConfigStore.stations().values().stream()
                    .filter(value -> station.id.equals(value.getCreateStationId()))
                    .map(value -> new CreateStationSnapshot(station.id, value.getCustomId(), value.getPosition()))
                    .findFirst().orElse(null);
        }
        var schedule = CreateScheduleResolver.resolve(train.runtime).orElse(null);
        var next = schedule == null ? null : resolve(schedule.nextStationFilter());
        if (train.navigation.destination != null) {
            var target = train.navigation.destination;
            next = StationLineConfigStore.stations().values().stream().filter(value -> target.id.equals(value.getCreateStationId()))
                    .map(value -> new CreateStationSnapshot(target.id,value.getCustomId(),value.getPosition())).findFirst().orElse(next);
        }
        var destination = schedule == null ? null : resolve(schedule.destinationFilter());
        return new CreateTrainSnapshot(train.id, train.name.getString(), current == null ? BlockPos.ZERO : current.position(),
                current, null, next, null, destination, Math.abs(train.speed) > 0.0001, train.speed);
    }
    public static boolean approaching(UUID id) {
        var train = Create.RAILWAYS.trains.get(id);
        return train != null && train.navigation.destination != null && train.navigation.distanceToDestination > 0
                && train.navigation.distanceToDestination <= 128;
    }
    public static boolean terminal(UUID id){
        var train=Create.RAILWAYS.trains.get(id);if(train==null||train.getCurrentStation()==null)return false;
        var schedule=train.runtime.getSchedule();if(schedule==null||schedule.cyclic)return false;
        for(int i=train.runtime.currentEntry+1;i<schedule.entries.size();i++)if(schedule.entries.get(i).instruction instanceof com.simibubi.create.content.trains.schedule.destination.DestinationInstruction)return false;
        return true;
    }
    public static Optional<CreateTrainSnapshot> trainAtStation(UUID stationId) {
        var adapter = new Create6TrainAdapter();
        return Create.RAILWAYS.trains.values().stream().filter(train -> train.getCurrentStation() != null && train.getCurrentStation().id.equals(stationId))
                .findFirst().map(adapter::snapshot);
    }
    private static CreateStationSnapshot resolve(String filter) {
        return CreateStationMatcher.match(filter)
                .map(value -> new CreateStationSnapshot(value.getCreateStationId(), value.getCustomId(), value.getPosition()))
                .orElse(null);
    }
}
