package com.misaka10843.createrailwayannouncer;

import com.misaka10843.createrailwayannouncer.command.CreateRailwayAnnouncerCommands;
import com.misaka10843.createrailwayannouncer.config.ClientConfig;
import com.misaka10843.createrailwayannouncer.config.ServerConfig;
import com.misaka10843.createrailwayannouncer.network.CreateRailwayAnnouncerNetworking;
import com.misaka10843.createrailwayannouncer.pack.VoicePackLoader;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;

@Mod(CreateRailwayAnnouncer.MODID)
public class CreateRailwayAnnouncer {
    public static final String MODID = "create_railway_announcer";
    public static final String NAME = "Create: Railway Announcer";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CreateRailwayAnnouncer(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(CreateRailwayAnnouncerNetworking::register);

        NeoForge.EVENT_BUS.register(this);

        modContainer.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        modContainer.registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC);
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        CreateRailwayAnnouncerCommands.register(event.getDispatcher());
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("{} common setup complete", NAME);
        event.enqueueWork(VoicePackLoader::reloadDefaultPack);
    }
}
