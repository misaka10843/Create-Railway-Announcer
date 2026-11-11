package com.misaka10843.create_railway_announcer.registry;

import com.misaka10843.create_railway_announcer.CreateRailwayAnnouncer;
import com.misaka10843.create_railway_announcer.blockentity.RailwaySpeakerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.*;
import net.neoforged.bus.api.IEventBus;

public final class ModBlockEntities {
    private static final DeferredRegister<BlockEntityType<?>> TYPES=DeferredRegister.create(net.minecraft.core.registries.Registries.BLOCK_ENTITY_TYPE,CreateRailwayAnnouncer.MODID);
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<RailwaySpeakerBlockEntity>> RAILWAY_SPEAKER=TYPES.register("railway_speaker",()->BlockEntityType.Builder.of(RailwaySpeakerBlockEntity::new,ModBlocks.STATION_SPEAKER.get(),ModBlocks.ONBOARD_SPEAKER.get()).build(null));
    public static void register(IEventBus bus){TYPES.register(bus);}
}
