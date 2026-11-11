package com.misaka10843.create_railway_announcer;

import com.misaka10843.create_railway_announcer.CreateRailwayAnnouncer;
import com.misaka10843.create_railway_announcer.announcement.ServerActiveAnnouncementRegistry;
import com.misaka10843.create_railway_announcer.announcement.TrainAnnouncementController;
import com.misaka10843.create_railway_announcer.config.ClientConfig;
import com.misaka10843.create_railway_announcer.config.ServerConfig;
import com.misaka10843.create_railway_announcer.config.StationLineConfigStore;
import com.misaka10843.create_railway_announcer.network.CreateRailwayAnnouncerNetworking;
import com.misaka10843.create_railway_announcer.pack.VoicePackLoader;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

@net.neoforged.fml.common.Mod(CreateRailwayAnnouncer.MODID)
public final class CreateRailwayAnnouncer {
    public static final String MODID = "create_railway_announcer";
    public static final Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    public CreateRailwayAnnouncer(IEventBus bus, ModContainer container) {
        com.misaka10843.create_railway_announcer.registry.ModBlocks.register(bus);
        com.misaka10843.create_railway_announcer.registry.ModBlockEntities.register(bus);
        container.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC, MODID + "-announcer-client.toml");
        container.registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC, MODID + "-announcer-server.toml");
        bus.addListener(CreateRailwayAnnouncerNetworking::register);
        bus.addListener((net.neoforged.neoforge.data.event.GatherDataEvent event) -> {if(event.includeClient())event.createProvider(com.misaka10843.create_railway_announcer.data.SpeakerDataProvider::new);});
        bus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(VoicePackLoader::reloadDefaultPack));
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> {
            StationLineConfigStore.refresh(event.getServer());
            com.misaka10843.create_railway_announcer.data.AnnouncementSavedData.syncIfChanged(event.getServer());
            if (!com.misaka10843.create_railway_announcer.data.AnnouncementSavedData.get(event.getServer()).enabled()) {
                TrainAnnouncementController.clear(); ServerActiveAnnouncementRegistry.clear(); return;
            }
            ServerActiveAnnouncementRegistry.serverTick(event.getServer());
            TrainAnnouncementController.tick(event.getServer());
        });
        NeoForge.EVENT_BUS.addListener(AnnouncerCommands::register);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) -> {
            if(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,new com.misaka10843.create_railway_announcer.network.AnnouncementDataPayload(com.misaka10843.create_railway_announcer.data.AnnouncementSavedData.get(player.getServer()).snapshot()));
        });
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStoppedEvent event) -> {
            TrainAnnouncementController.clear();
            StationLineConfigStore.clear();
            ServerActiveAnnouncementRegistry.clear();
            com.misaka10843.create_railway_announcer.announcement.ServerAnnouncementCooldown.clear();
            com.misaka10843.create_railway_announcer.announcement.source.ServerAnnouncementSourceRegistry.clear();
        });
    }
}
