package com.misaka10843.create_railway_announcer.client;

import com.misaka10843.create_railway_announcer.network.SpeakerScreenPayload;
import com.misaka10843.create_railway_announcer.announcement.AnnouncementEventType;
import com.misaka10843.create_railway_signage.client.RailwaySignageClientCache;
import com.misaka10843.create_railway_signage.compat.ldlib.LdlibUi;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

public final class SpeakerUi {
    private SpeakerUi() {}
    public static void open(SpeakerScreenPayload payload) {
        var root = LdlibUi.panel().layout(l -> l.paddingAll(6).gapAll(4));
        if (!payload.canEdit()) {
            var done = LdlibUi.primaryButton("gui.done",event -> Minecraft.getInstance().setScreen(null));
            LdlibUi.setPrimaryEnabled(done,true);
            root.addChildren(LdlibUi.heading(payload.onboard() ? "block.create_railway_announcer.onboard_speaker" : "block.create_railway_announcer.station_speaker"),
                    LdlibUi.text(Component.translatable("announcer.create_railway_announcer.speaker_enabled").append(": ")
                            .append(Component.translatable(payload.enabled() ? "options.on" : "options.off"))),
                    LdlibUi.text("announcer.create_railway_announcer.denied"),
                    LdlibUi.button("announcer.create_railway_announcer.personal_settings",event -> AnnouncerSettingsUi.open(Minecraft.getInstance().screen)),done);
            LdlibUi.open(root,Component.translatable("announcer.create_railway_announcer.speaker_title"),LdlibUi.responsive(.65f,300,520,.55f,150,280));
            return;
        }
        var list = LdlibUi.scrollList(); list.layout(l -> l.flex(1));
        boolean[] enabled = {payload.enabled()};
        String[] selected = {payload.platformId()};
        AnnouncementEventType[] event = {payload.event()};
        var card = LdlibUi.card();
        card.addChildren(LdlibUi.heading(payload.onboard() ? "block.create_railway_announcer.onboard_speaker" : "block.create_railway_announcer.station_speaker"),
                LdlibUi.stateToggle(Component.translatable("announcer.create_railway_announcer.speaker_enabled"), enabled[0], () -> enabled[0] = !enabled[0]).root());
        var horizontal = LdlibUi.numberField(payload.horizontal(),64,4,128);
        var vertical = LdlibUi.numberField(payload.vertical(),64,2,64);
        if (payload.onboard()) card.addChild(LdlibUi.wrappedText("announcer.create_railway_announcer.onboard_hint"));
        else {
            var group = RailwaySignageClientCache.stationGroup(payload.stationId()).orElse(null);
            card.addChild(LdlibUi.text(group == null ? Component.translatable("announcer.create_railway_announcer.no_stations") : Component.literal(group.japaneseName())));
            List<String> ids = new ArrayList<>(); ids.add("");
            if (group != null) group.platforms().stream().filter(binding -> Minecraft.getInstance().level != null && binding.dimension().equals(Minecraft.getInstance().level.dimension()))
                    .forEach(binding -> ids.add(binding.platformId()));
            var eventPicker=LdlibUi.selector(com.misaka10843.create_railway_announcer.announcement.AnnouncementSequenceMapper.manualEvents().stream()
                            .filter(value->com.misaka10843.create_railway_announcer.announcement.AnnouncementSequenceMapper.channel(value)!=com.misaka10843.create_railway_announcer.audio.AudioChannel.ONBOARD_VOICE).toList(),
                    value->Component.translatable("announcer.create_railway_announcer.event."+value.name().toLowerCase(Locale.ROOT)),value->event[0]=value);
            eventPicker.setValue(event[0],false);
            card.addChildren(LdlibUi.choices(ids, selected[0], id -> id.isBlank() ? Component.translatable("announcer.create_railway_announcer.all_platforms") : Component.literal(id),
                            id -> selected[0] = id).root(),
                    LdlibUi.formRow("announcer.create_railway_announcer.horizontal_range",100,horizontal),
                    LdlibUi.formRow("announcer.create_railway_announcer.vertical_range",100,vertical),
                    LdlibUi.text("announcer.create_railway_announcer.redstone_event"),
                    eventPicker,
                    LdlibUi.button("announcer.create_railway_announcer.test_saved", click -> PacketDistributor.sendToServer(new SpeakerScreenPayload.Test(payload.pos(),selected[0],event[0]))));
        }
        list.addScrollViewChild(card);
        var status = LdlibUi.text(Component.empty());
        var save = LdlibUi.primaryButton("announcer.create_railway_announcer.save", click -> {
            try {
                PacketDistributor.sendToServer(new SpeakerScreenPayload.Save(payload.pos(), selected[0],
                        Integer.parseInt(horizontal.getText()),Integer.parseInt(vertical.getText()),event[0],enabled[0]));
                Minecraft.getInstance().setScreen(null);
            } catch (RuntimeException error) { status.setText(Component.translatable("announcer.create_railway_announcer.invalid")); }
        });
        Runnable validate = () -> {
            try {
                int h = Integer.parseInt(horizontal.getText()), v = Integer.parseInt(vertical.getText());
                LdlibUi.setPrimaryEnabled(save,(payload.onboard() || !payload.stationId().isBlank()) && h >= 4 && h <= 128 && v >= 2 && v <= 64);
            } catch (RuntimeException error) { LdlibUi.setPrimaryEnabled(save,false); status.setText(Component.translatable("announcer.create_railway_announcer.invalid")); }
        };
        horizontal.registerValueListener(value -> validate.run()); vertical.registerValueListener(value -> validate.run()); validate.run();
        root.addChildren(list,LdlibUi.wrappedText("announcer.create_railway_announcer.speaker_hint"),status,save);
        LdlibUi.open(root,Component.translatable("announcer.create_railway_announcer.speaker_title"),LdlibUi.responsive(.7f,340,600,.85f,220,440));
    }
}
