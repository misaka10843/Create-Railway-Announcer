package com.misaka10843.create_railway_announcer.registry;

import com.misaka10843.create_railway_announcer.CreateRailwayAnnouncer;
import com.misaka10843.create_railway_announcer.block.RailwaySpeakerBlock;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.*;
import net.neoforged.bus.api.IEventBus;

public final class ModBlocks {
    private static final DeferredRegister.Blocks BLOCKS=DeferredRegister.createBlocks(CreateRailwayAnnouncer.MODID);
    private static final DeferredRegister.Items ITEMS=DeferredRegister.createItems(CreateRailwayAnnouncer.MODID);
    private static final DeferredRegister<CreativeModeTab> TABS=DeferredRegister.create(net.minecraft.core.registries.Registries.CREATIVE_MODE_TAB,CreateRailwayAnnouncer.MODID);
    public static final DeferredBlock<RailwaySpeakerBlock> STATION_SPEAKER=BLOCKS.registerBlock("station_speaker",p->new RailwaySpeakerBlock(p,false),BlockBehaviour.Properties.of().strength(2).noOcclusion());
    public static final DeferredBlock<RailwaySpeakerBlock> ONBOARD_SPEAKER=BLOCKS.registerBlock("onboard_speaker",p->new RailwaySpeakerBlock(p,true),BlockBehaviour.Properties.of().strength(2).noOcclusion());
    static {
        ITEMS.registerSimpleBlockItem(STATION_SPEAKER); ITEMS.registerSimpleBlockItem(ONBOARD_SPEAKER);
        TABS.register("main",()->CreativeModeTab.builder().title(net.minecraft.network.chat.Component.translatable("itemGroup.create_railway_announcer.main"))
                .icon(()->new ItemStack(STATION_SPEAKER.get())).displayItems((parameters,output)->{output.accept(STATION_SPEAKER.get());output.accept(ONBOARD_SPEAKER.get());}).build());
    }
    public static void register(IEventBus bus){BLOCKS.register(bus);ITEMS.register(bus);TABS.register(bus);}
}
