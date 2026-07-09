package com.misaka10843.createrailwayannouncer;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = CreateRailwayAnnouncer.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = CreateRailwayAnnouncer.MODID, value = Dist.CLIENT)
public class CreateRailwayAnnouncerClient {
    public CreateRailwayAnnouncerClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        CreateRailwayAnnouncer.LOGGER.info("{} client setup complete", CreateRailwayAnnouncer.NAME);
    }
}
