package com.misaka10843.create_railway_announcer;

import com.misaka10843.create_railway_announcer.announcement.*;
import com.misaka10843.create_railway_announcer.audio.AudioChannel;
import com.misaka10843.create_railway_announcer.config.StationLineConfigStore;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class AnnouncerCommands {
    private AnnouncerCommands() {}
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("crs").requires(source -> source.hasPermission(2))
                .then(Commands.literal("announcer")
                        .then(Commands.literal("test")
                                .then(Commands.argument("platform", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(StationLineConfigStore.stations().keySet(), builder))
                                        .executes(context -> {
                                            var station = StationLineConfigStore.station(StringArgumentType.getString(context, "platform")).orElse(null);
                                            if (station == null) return 0;
                                            var request = ServerAnnouncementRequest.of(AnnouncementEventType.PLATFORM_DOOR_CLOSING,
                                                    null, "", station.getCreateStationId(), station.getPosition(),
                                                    station.getCustomId(), "", "", AudioChannel.PLATFORM_VOICE, 90);
                                            var result = ServerAnnouncementDispatcher.dispatchToNearbyPlayers(context.getSource().getLevel(), request);
                                            context.getSource().sendSuccess(result::feedback, false);
                                            return 1;
                                        })))));
    }
}
