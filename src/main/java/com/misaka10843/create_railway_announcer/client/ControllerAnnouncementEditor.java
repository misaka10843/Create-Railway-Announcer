package com.misaka10843.create_railway_announcer.client;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.misaka10843.create_railway_signage.compat.ldlib.LdlibUi;
import com.misaka10843.create_railway_announcer.model.StationAnnouncementSettings;
import com.misaka10843.create_railway_signage.network.PlatformEditData;
import com.misaka10843.create_railway_announcer.config.DoorSide;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.function.Supplier;

public final class ControllerAnnouncementEditor {
    private boolean enabled, global;
    private final boolean admin;
    private final boolean initialGlobal;
    private final boolean editable;
    private static final Map<String,String> LABELS = Map.of(
            "reading","announcer.create_railway_announcer.reading", "voice","announcer.create_railway_announcer.voice",
            "speed","announcer.create_railway_announcer.speed", "pitch","announcer.create_railway_announcer.pitch",
            "next_stop_ssml","announcer.create_railway_announcer.next_stop_ssml", "arrival_ssml","announcer.create_railway_announcer.arrival_ssml",
            "closing_ssml","announcer.create_railway_announcer.closing_ssml");
    private static final Map<String,String> PLATFORM_LABELS = Map.of(
            "platform_voice","announcer.create_railway_announcer.platform_voice", "platform_speed","announcer.create_railway_announcer.platform_speed",
            "platform_pitch","announcer.create_railway_announcer.platform_pitch", "platform_approach_ssml","announcer.create_railway_announcer.platform_approach_ssml",
            "platform_arrival_ssml","announcer.create_railway_announcer.platform_arrival_ssml", "platform_closing_ssml","announcer.create_railway_announcer.platform_closing_ssml");
    private final Map<String, String> fields = new LinkedHashMap<>();
    private final Map<String,String> eventSsml;
    private final Map<String, DoorSide> doors;
    private final Supplier<List<PlatformEditData>> platforms;
    private final Runnable changed;
    private ScrollerView platformList;
    private final net.minecraft.core.BlockPos controllerPos;
    private com.misaka10843.create_railway_announcer.announcement.AnnouncementEventType testEvent = com.misaka10843.create_railway_announcer.announcement.AnnouncementEventType.PLATFORM_ARRIVAL;

    public ControllerAnnouncementEditor(StationAnnouncementSettings value, boolean global, boolean admin, boolean editable,
                                         Supplier<List<PlatformEditData>> platforms, Runnable changed, net.minecraft.core.BlockPos controllerPos) {
        enabled = value.enabled(); this.global = global; this.admin = admin; this.platforms = platforms; this.changed = changed;
        initialGlobal = global;
        this.editable = editable;
        fields.put("reading", value.reading()); fields.put("voice", value.voice());
        fields.put("speed", value.speed() == 0 ? "" : Double.toString(value.speed())); fields.put("pitch", value.pitch());
        fields.put("next_stop_ssml", value.nextStopSsml()); fields.put("arrival_ssml", value.arrivalSsml()); fields.put("closing_ssml", value.closingSsml());
        fields.put("platform_voice",value.platformVoice()); fields.put("platform_speed",value.platformSpeed() == 0 ? "" : Double.toString(value.platformSpeed()));
        fields.put("platform_pitch",value.platformPitch()); fields.put("platform_approach_ssml",value.platformApproachSsml());
        fields.put("platform_arrival_ssml",value.platformArrivalSsml()); fields.put("platform_closing_ssml",value.platformClosingSsml());
        doors = new LinkedHashMap<>(value.doors());
        eventSsml=new LinkedHashMap<>(value.eventSsml());
        this.controllerPos = controllerPos;
    }
    public UIElement build() {
        var card = LdlibUi.card().layout(l -> l.flexGrow(1).flexShrink(1).minHeight(0));
        var list = LdlibUi.scrollList(); list.layout(l -> l.flexGrow(1).flexShrink(1).minHeight(0));
        list.addScrollViewChild(LdlibUi.heading("announcer.create_railway_announcer.station"));
        if (!editable) {
            list.addScrollViewChild(LdlibUi.text("announcer.create_railway_announcer.denied"));
            list.addScrollViewChild(LdlibUi.button("announcer.create_railway_announcer.personal_settings", event -> AnnouncerSettingsUi.open(net.minecraft.client.Minecraft.getInstance().screen)));
            card.addChild(list);
            return card;
        }
        list.addScrollViewChild(LdlibUi.stateToggle(Component.translatable("announcer.create_railway_announcer.station_enabled"), enabled,
                () -> { enabled = !enabled; changed.run(); }).root());
        if (admin) list.addScrollViewChild(LdlibUi.stateToggle(Component.translatable("announcer.create_railway_announcer.global_enabled"), global,
                () -> { global = !global; changed.run(); }).root());
        else list.addScrollViewChild(LdlibUi.text(global ? "announcer.create_railway_announcer.global_on" : "announcer.create_railway_announcer.global_off"));
        list.addScrollViewChild(LdlibUi.wrappedText("announcer.create_railway_announcer.station_audio_hint"));
        fields.forEach((name, value) -> {
            if (name.equals("voice")) list.addScrollViewChild(LdlibUi.heading("announcer.create_railway_announcer.onboard_section"));
            if (name.equals("platform_voice")) list.addScrollViewChild(LdlibUi.heading("announcer.create_railway_announcer.platform_section"));
            String label = name.startsWith("platform_") ? PLATFORM_LABELS.get(name) : LABELS.get(name);
            if (name.endsWith("_ssml")) {
                return;
            }
            var field = LdlibUi.formField(value);
            field.registerValueListener(text -> { fields.put(name, text); changed.run(); });
            LdlibUi.tooltip(field, Component.translatable("announcer.create_railway_announcer.station_audio_hint"));
            list.addScrollViewChild(LdlibUi.formRow(label, 82, field));
            if(name.equals("pitch"))list.addScrollViewChild(templates(com.misaka10843.create_railway_announcer.audio.AudioChannel.ONBOARD_VOICE));
            if(name.equals("platform_pitch"))list.addScrollViewChild(templates(com.misaka10843.create_railway_announcer.audio.AudioChannel.PLATFORM_VOICE));
        });
        var testPicker=LdlibUi.selector(com.misaka10843.create_railway_announcer.announcement.AnnouncementSequenceMapper.manualEvents(),
                event -> Component.translatable("announcer.create_railway_announcer.event."+event.name().toLowerCase(Locale.ROOT)),event -> testEvent=event);
        testPicker.setValue(testEvent,false);list.addScrollViewChild(testPicker);
        platformList = LdlibUi.scrollList();
        platformList.layout(l -> l.height(100).minHeight(70).widthStretch());
        list.addScrollViewChild(platformList);
        refreshPlatforms();
        list.addScrollViewChild(LdlibUi.wrappedText("announcer.create_railway_announcer.speaker_hint"));
        list.addScrollViewChild(LdlibUi.button("announcer.create_railway_announcer.personal_settings", event -> {
            var parent = net.minecraft.client.Minecraft.getInstance().screen;
            AnnouncerSettingsUi.open(parent);
        }));
        card.addChild(list);
        return card;
    }
    public void refreshPlatforms() {
        if (platformList == null) return;
        platformList.clearAllScrollViewChildren();
        platformList.addScrollViewChild(LdlibUi.text("announcer.create_railway_announcer.door"));
        for (var platform : platforms.get()) {
            String id = platform.platformId();
            var side = LdlibUi.choices(List.of(DoorSide.values()), doors.getOrDefault(id.toLowerCase(Locale.ROOT), DoorSide.NONE),
                    value -> Component.translatable("announcer.create_railway_announcer.door." + value.name().toLowerCase(Locale.ROOT)),
                    value -> doors.put(id.toLowerCase(Locale.ROOT), value));
            var row = LdlibUi.column();
            row.addChildren(LdlibUi.text(Component.literal(id)), side.root(),
                    LdlibUi.button("announcer.create_railway_announcer.test_saved", event ->
                            net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                                    new com.misaka10843.create_railway_announcer.network.SpeakerScreenPayload.Test(controllerPos,id,
                                            testEvent))),
                    LdlibUi.button("announcer.create_railway_announcer.stop_station",event->net.neoforged.neoforge.network.PacketDistributor.sendToServer(new com.misaka10843.create_railway_announcer.network.ControllerAudioPayload.Stop(controllerPos,id))));
            platformList.addScrollViewChild(row);
        }
    }
    public boolean globalEnabled() { return global; }
    private UIElement templates(com.misaka10843.create_railway_announcer.audio.AudioChannel channel){
        var events=com.misaka10843.create_railway_announcer.announcement.AnnouncementSequenceMapper.speechEvents(channel);
        var group=LdlibUi.column();var panes=new LinkedHashMap<com.misaka10843.create_railway_announcer.announcement.AnnouncementEventType,UIElement>();
        var picker=LdlibUi.selector(events,event->Component.translatable("announcer.create_railway_announcer.event."+event.name().toLowerCase(Locale.ROOT)),event->panes.forEach((key,pane)->pane.setDisplay(key==event)));
        picker.setValue(events.getFirst(),false);group.addChild(picker);
        for(var event:events){
            String field=switch(event){case ONBOARD_NEXT_STOP->"next_stop_ssml";case ONBOARD_ARRIVED->"arrival_ssml";case DOOR_CLOSING->"closing_ssml";case PLATFORM_APPROACH->"platform_approach_ssml";case PLATFORM_ARRIVAL->"platform_arrival_ssml";case PLATFORM_DOOR_CLOSING->"platform_closing_ssml";default->"";};
            String key=event.name().toLowerCase(Locale.ROOT);String value=field.isBlank()?eventSsml.getOrDefault(key,""):fields.get(field);
            var area=LdlibUi.textArea(value,80,text->{if(field.isBlank())eventSsml.put(key,text);else fields.put(field,text);changed.run();});
            area.setDisplay(event==events.getFirst());panes.put(event,area);group.addChild(area);
        }
        return group;
    }
    public Boolean globalChange() { return global == initialGlobal ? null : global; }
    public StationAnnouncementSettings value() {
        Map<String, DoorSide> current = new LinkedHashMap<>();
        for (var platform : platforms.get()) current.put(platform.platformId().toLowerCase(Locale.ROOT), doors.getOrDefault(platform.platformId().toLowerCase(Locale.ROOT), DoorSide.NONE));
        String speed = fields.get("speed").trim();
        String platformSpeed = fields.get("platform_speed").trim();
        return new StationAnnouncementSettings(enabled, fields.get("reading"), fields.get("voice"), speed.isBlank() ? 0 : Double.parseDouble(speed),
                fields.get("pitch"), fields.get("next_stop_ssml"), fields.get("arrival_ssml"), fields.get("closing_ssml"), current,
                fields.get("platform_voice"),platformSpeed.isBlank() ? 0 : Double.parseDouble(platformSpeed),fields.get("platform_pitch"),
                fields.get("platform_approach_ssml"),fields.get("platform_arrival_ssml"),fields.get("platform_closing_ssml"),eventSsml);
    }
}
