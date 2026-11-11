package com.misaka10843.create_railway_announcer.client;

import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.misaka10843.create_railway_announcer.config.ClientConfig;
import com.misaka10843.create_railway_announcer.client.runtime.ClientAnnouncementServices;
import com.misaka10843.create_railway_announcer.audio.*;
import com.misaka10843.create_railway_announcer.tts.*;
import com.misaka10843.create_railway_signage.compat.ldlib.LdlibUi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.concurrent.CompletableFuture;

public final class AnnouncerSettingsUi {
    private AnnouncerSettingsUi() {}
    public static void open() { open(Minecraft.getInstance().screen); }
    public static void open(Screen parent) { Minecraft.getInstance().setScreen(createScreen(parent)); }

    public static Screen createScreen(Screen parent) {
        var root = LdlibUi.panel().layout(l -> l.paddingAll(7).gapAll(4));
        var list = LdlibUi.scrollList(); list.layout(l -> l.flex(1));
        Label status = LdlibUi.text(Component.empty());
        status.setOverflowVisible(false);
        var personal = LdlibUi.card();
        boolean[] chimes = {ClientConfig.DOOR_CHIMES_ENABLED.get()};
        boolean[] onboard = {ClientConfig.ONBOARD_ENABLED.get()}, platform = {ClientConfig.PLATFORM_ENABLED.get()};
        boolean[] playback = {ClientConfig.ANNOUNCEMENTS_ENABLED.get()}, preload = {ClientConfig.PRELOAD_ENABLED.get()},
                enabled = {ClientConfig.TTS_ENABLED.get()}, subtitles = {ClientConfig.SUBTITLES_ENABLED.get()};
        personal.addChildren(LdlibUi.heading("announcer.create_railway_announcer.personal"),
                LdlibUi.stateToggle(Component.translatable("announcer.create_railway_announcer.playback_enabled"), playback[0], () -> playback[0] = !playback[0]).root(),
                LdlibUi.stateToggle(Component.translatable("announcer.create_railway_announcer.onboard_enabled"), onboard[0], () -> onboard[0] = !onboard[0]).root(),
                LdlibUi.stateToggle(Component.translatable("announcer.create_railway_announcer.platform_enabled"), platform[0], () -> platform[0] = !platform[0]).root(),
                LdlibUi.stateToggle(Component.translatable("announcer.create_railway_announcer.door_chimes"), chimes[0], () -> chimes[0] = !chimes[0]).root(),
                LdlibUi.stateToggle(Component.translatable("announcer.create_railway_announcer.preload_enabled"), preload[0], () -> preload[0] = !preload[0]).root(),
                LdlibUi.stateToggle(Component.translatable("announcer.create_railway_announcer.enabled"), enabled[0], () -> enabled[0] = !enabled[0]).root(),
                LdlibUi.stateToggle(Component.translatable("announcer.create_railway_announcer.subtitles"), subtitles[0], () -> subtitles[0] = !subtitles[0]).root());
        var settings = LdlibUi.card();
        String[] selectedPack={ClientConfig.VOICE_PACK.get()};
        var packPicker=LdlibUi.selector(com.misaka10843.create_railway_announcer.pack.VoicePackLoader.availablePacks(),Component::literal,value->selectedPack[0]=value);
        packPicker.setValue(selectedPack[0],false);
        var endpoint = LdlibUi.formField(ClientConfig.TTS_ENDPOINT.get());
        var voice = LdlibUi.formField(ClientConfig.TTS_PREFERRED_VOICE_CONTAINS.get());
        var language = LdlibUi.formField(ClientConfig.TTS_LANGUAGE.get());
        var speed = LdlibUi.formField(Double.toString(ClientConfig.TTS_RATE.get()));
        var pitch = LdlibUi.formField(ClientConfig.TTS_PITCH.get());
        var token = LdlibUi.formField(ClientConfig.TTS_TOKEN.get());
        var timeout = LdlibUi.formField(Integer.toString(ClientConfig.TTS_TIMEOUT.get()));
        settings.addChildren(LdlibUi.heading("announcer.create_railway_announcer.settings"),
                LdlibUi.formRow("announcer.create_railway_announcer.voice_pack",70,packPicker),
                LdlibUi.formRow("announcer.create_railway_announcer.endpoint", 70, endpoint, true),
                LdlibUi.formRow("announcer.create_railway_announcer.voice", 70, voice, true),
                LdlibUi.formRow("announcer.create_railway_announcer.language", 70, language, true),
                LdlibUi.formRow("announcer.create_railway_announcer.speed", 70, speed, true),
                LdlibUi.formRow("announcer.create_railway_announcer.pitch", 70, pitch, true),
                LdlibUi.formRow("announcer.create_railway_announcer.token", 70, token),
                LdlibUi.formRow("announcer.create_railway_announcer.timeout", 70, timeout, true));
        var test = LdlibUi.card();
        var input = LdlibUi.textArea("次は、秋葉原、秋葉原です。",64,value -> {});
        boolean[] ssml = {false};
        var ssmlToggle = LdlibUi.stateToggle(Component.translatable("announcer.create_railway_announcer.ssml"), false, () -> ssml[0] = !ssml[0]);
        test.addChildren(LdlibUi.heading("announcer.create_railway_announcer.test"), input, ssmlToggle.root(),
                LdlibUi.button("announcer.create_railway_announcer.preview", event -> {
                    try {
                        validate(endpoint.getText(), voice.getText(), language.getText(), speed.getText(), pitch.getText(), timeout.getText());
                        String text = String.join("\n",input.getValue());
                        if (text.isBlank()) throw new IllegalArgumentException();
                        if (ssml[0]) Ssml.validate(text);
                        status.setText(Component.translatable("announcer.create_railway_announcer.generating"));
                        var fragment = new AudioFragmentKey(AudioFragmentType.COMMON, "preview", "default", TtsBackend.HTTP,
                                language.getText(), voice.getText(), Double.parseDouble(speed.getText()), ClientConfig.TTS_VOLUME.get(), text, ssml[0]);
                        var provider = new HttpTtsProvider(new HttpTtsProvider.Settings(endpoint.getText().trim(),
                                token.getText().trim(), pitch.getText().trim(), Integer.parseInt(timeout.getText()), true));
                        new AudioCache(provider).resolveOrGenerate(fragment).thenAccept(result -> Minecraft.getInstance().execute(() -> {
                            if (Minecraft.getInstance().screen == null) return;
                            if (result.ok()) {
                                ClientAnnouncementServices.localBackend().preview(result.audioPath()).exceptionally(error -> {
                                    Minecraft.getInstance().execute(() -> status.setText(Component.translatable("announcer.create_railway_announcer.failed", error.toString())));
                                    return null;
                                });
                                status.setText(Component.translatable("announcer.create_railway_announcer.playing"));
                            } else {
                                status.setText(Component.translatable("announcer.create_railway_announcer.failed", result.error()));
                                LdlibUi.tooltip(status, Component.literal(result.error()));
                            }
                        }));
                    } catch (RuntimeException error) { status.setText(Component.translatable("announcer.create_railway_announcer.invalid")); }
                }), LdlibUi.button("announcer.create_railway_announcer.stop", event -> ClientAnnouncementServices.disconnect()));
        var cache = LdlibUi.card();
        var stats = LdlibUi.wrappedText(Component.empty());
        Runnable refresh = () -> CompletableFuture.supplyAsync(() -> AudioCache.cacheFiles().stats()).whenComplete((value, error) ->
                Minecraft.getInstance().execute(() -> {
                    if (error != null) stats.setText(Component.translatable("announcer.create_railway_announcer.cache_failed"));
                    else stats.setText(Component.translatable("announcer.create_railway_announcer.cache_stats", value.files(),
                            String.format(java.util.Locale.ROOT, "%.2f", value.bytes() / 1048576d), value.pending(), ClientAnnouncementServices.remainingPreloads()));
                }));
        cache.addChildren(LdlibUi.heading("announcer.create_railway_announcer.cache"), stats,
                LdlibUi.button("announcer.create_railway_announcer.refresh_cache", event -> refresh.run()),
                LdlibUi.button("announcer.create_railway_announcer.pregenerate", event -> {
                    status.setText(Component.translatable("announcer.create_railway_announcer.generating"));
                    ClientAnnouncementServices.preload().whenComplete((report, error) -> Minecraft.getInstance().execute(() -> {
                        status.setText(Component.translatable(error == null && report.failures() == 0 && !report.cancelled() ? "announcer.create_railway_announcer.cache_ready" : "announcer.create_railway_announcer.cache_failed")); refresh.run();
                    }));
                }),
                LdlibUi.button("announcer.create_railway_announcer.delete_cache", event -> {
                    ClientAnnouncementServices.disconnect();
                    CompletableFuture.supplyAsync(() -> AudioCache.cacheFiles().clear()).whenComplete((value, error) ->
                            Minecraft.getInstance().execute(() -> {
                                status.setText(error != null ? Component.translatable("announcer.create_railway_announcer.cache_failed")
                                        : Component.translatable("announcer.create_railway_announcer.cache_deleted", value.deleted(), value.failed())); refresh.run();
                            }));
                }));
        refresh.run();
        for (var card : java.util.List.of(personal, settings, test, cache)) list.addScrollViewChild(card);
        var save = LdlibUi.primaryButton("announcer.create_railway_announcer.save", event -> {
            try {
                validate(endpoint.getText(), voice.getText(), language.getText(), speed.getText(), pitch.getText(), timeout.getText());
                ClientAnnouncementServices.disconnect();
                com.misaka10843.create_railway_announcer.pack.VoicePackLoader.reloadPack(selectedPack[0],language.getText().trim());
                ClientConfig.VOICE_PACK.set(selectedPack[0]);
                ClientConfig.TTS_ENDPOINT.set(endpoint.getText().trim()); ClientConfig.TTS_TOKEN.set(token.getText().trim());
                ClientConfig.TTS_PREFERRED_VOICE_CONTAINS.set(voice.getText().trim()); ClientConfig.TTS_LANGUAGE.set(language.getText().trim());
                ClientConfig.TTS_RATE.set(Double.parseDouble(speed.getText())); ClientConfig.TTS_PITCH.set(pitch.getText().trim());
                ClientConfig.TTS_TIMEOUT.set(Integer.parseInt(timeout.getText()));
                ClientConfig.TTS_ENABLED.set(enabled[0]); ClientConfig.ANNOUNCEMENTS_ENABLED.set(playback[0]);
                ClientConfig.DOOR_CHIMES_ENABLED.set(chimes[0]);
                ClientConfig.ONBOARD_ENABLED.set(onboard[0]); ClientConfig.PLATFORM_ENABLED.set(platform[0]);
                ClientConfig.PRELOAD_ENABLED.set(preload[0]); ClientConfig.SUBTITLES_ENABLED.set(subtitles[0]); ClientConfig.SPEC.save();
                ClientAnnouncementServices.disconnect();
                if (playback[0] && preload[0]) ClientAnnouncementServices.preloadIfEnabled();
                status.setText(Component.translatable("announcer.create_railway_announcer.saved"));
            } catch (Exception error) { status.setText(Component.translatable("announcer.create_railway_announcer.failed",error.getMessage())); }
        });
        Runnable update = () -> {
            try { validate(endpoint.getText(), voice.getText(), language.getText(), speed.getText(), pitch.getText(), timeout.getText()); LdlibUi.setPrimaryEnabled(save, true); }
            catch (RuntimeException error) { LdlibUi.setPrimaryEnabled(save, false); status.setText(Component.translatable("announcer.create_railway_announcer.invalid")); }
        };
        for (var field : java.util.List.of(endpoint, voice, language, speed, pitch, timeout)) field.registerValueListener(value -> update.run());
        update.run();
        var footer = LdlibUi.row(22);
        footer.addChildren(LdlibUi.button("gui.done", event -> Minecraft.getInstance().setScreen(parent)), LdlibUi.spacer(), save);
        root.addChildren(LdlibUi.heading("announcer.create_railway_announcer.title"), list, LdlibUi.wrappedText("announcer.create_railway_announcer.hint"), status, footer);
        return new ModularUIScreen(ModularUI.of(LdlibUi.ui(root, LdlibUi.responsive(.85f, 350, 650, .9f, 240, 500))),
                Component.translatable("announcer.create_railway_announcer.title")) {
            @Override public void onClose() { Minecraft.getInstance().setScreen(parent); }
        };
    }

    private static void validate(String endpoint, String voice, String language, String speed, String pitch, String timeout) {
        double rate = Double.parseDouble(speed);
        int seconds = Integer.parseInt(timeout);
        var uri = java.net.URI.create(endpoint.trim());
        if (uri.getHost() == null || !("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()))
                || voice.isBlank() || language.isBlank() || pitch.isBlank() || !Double.isFinite(rate) || rate < .5 || rate > 2 || seconds < 1 || seconds > 120) throw new IllegalArgumentException();
    }
}
