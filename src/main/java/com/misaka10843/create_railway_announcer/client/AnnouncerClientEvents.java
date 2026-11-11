package com.misaka10843.create_railway_announcer.client;

import com.misaka10843.create_railway_announcer.CreateRailwayAnnouncer;
import com.misaka10843.create_railway_announcer.client.runtime.ClientAnnouncementRuntime;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.minecraft.commands.Commands;

@EventBusSubscriber(modid = CreateRailwayAnnouncer.MODID, value = Dist.CLIENT)
public final class AnnouncerClientEvents {
    public static void refreshProjection() {
        com.misaka10843.create_railway_announcer.config.StationLineConfigStore.apply(com.misaka10843.create_railway_signage.client.RailwaySignageClientCache.stationGroups(),com.misaka10843.create_railway_signage.client.RailwaySignageClientCache.lines());
        if (!com.misaka10843.create_railway_announcer.config.AnnouncementClientData.enabled()) com.misaka10843.create_railway_announcer.client.runtime.ClientAnnouncementServices.disconnect();
        ClientAnnouncementRuntime.stopDisabledStations();
        com.misaka10843.create_railway_announcer.client.runtime.ClientAnnouncementServices.preloadIfEnabled();
    }
    @SubscribeEvent public static void signageData(com.misaka10843.create_railway_signage.api.event.RailwayDataUpdatedEvent event) { refreshProjection(); }
    private static final net.minecraft.client.KeyMapping SETTINGS = new net.minecraft.client.KeyMapping(
            "key.create_railway_announcer.announcements", org.lwjgl.glfw.GLFW.GLFW_KEY_UNKNOWN, "key.categories.create_railway_announcer");
    private AnnouncerClientEvents() {}
    @SubscribeEvent public static void frame(net.neoforged.neoforge.client.event.RenderFrameEvent.Pre event){
        var game=net.minecraft.client.Minecraft.getInstance();
        com.misaka10843.create_railway_announcer.client.audio.LocalOggAudioPlayer.pauseGame(game.level!=null && game.isPaused());
    }
    public static void keys(net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent event) { event.register(SETTINGS); }
    @SubscribeEvent public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        while (SETTINGS.consumeClick()) AnnouncerSettingsUi.open();
        var level = net.minecraft.client.Minecraft.getInstance().level;
        if (level != null && level.getGameTime() % 10 == 0 && com.misaka10843.create_railway_announcer.client.runtime.ClientAnnouncementServices.isInitialized())
            ClientAnnouncementRuntime.stopInvalidAudience();
    }
    public static void setup(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event) {
        event.enqueueWork(com.misaka10843.create_railway_announcer.pack.VoicePackLoader::reloadDefaultPack);
        event.enqueueWork(() -> com.misaka10843.create_railway_signage.api.client.StationControllerActions.register(CreateRailwayAnnouncer.MODID,
                "announcer.create_railway_announcer.station",ControllerAudioUi::request));
        event.enqueueWork(() -> net.neoforged.fml.ModList.get().getModContainerById(CreateRailwayAnnouncer.MODID).orElseThrow()
                .registerExtensionPoint(net.neoforged.neoforge.client.gui.IConfigScreenFactory.class,
                        (container, parent) -> AnnouncerSettingsUi.createScreen(parent)));
    }
    @EventBusSubscriber(modid=CreateRailwayAnnouncer.MODID,value=Dist.CLIENT)
    public static final class ModEvents {
        @SubscribeEvent public static void keys(net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent event){AnnouncerClientEvents.keys(event);}
        @SubscribeEvent public static void setup(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event){AnnouncerClientEvents.setup(event);}
    }
    @SubscribeEvent public static void commands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("crs_announcer").executes(context -> {
            net.minecraft.client.Minecraft.getInstance().execute(AnnouncerSettingsUi::open);
            return 1;
        }).then(Commands.literal("reload").executes(context -> {
            com.misaka10843.create_railway_announcer.pack.VoicePackLoader.reloadDefaultPack();
            context.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.translatable("announcer.create_railway_announcer.reloaded"), false);
            return 1;
        })));
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        com.misaka10843.create_railway_announcer.playback.PlaybackClock.GAME.paused(false);
        com.misaka10843.create_railway_announcer.client.runtime.ClientAnnouncementPacketClientHandler.reset();
        com.misaka10843.create_railway_announcer.client.runtime.ClientAnnouncementServices.disconnect();
        com.misaka10843.create_railway_announcer.config.StationLineConfigStore.clear();
        com.misaka10843.create_railway_announcer.config.AnnouncementClientData.clear();
    }
}
