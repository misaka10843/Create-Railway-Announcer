import com.misaka10843.create_railway_announcer.tts.*;
import com.misaka10843.create_railway_announcer.audio.*;
import com.misaka10843.create_railway_announcer.config.*;
import com.misaka10843.create_railway_announcer.network.*;
import com.misaka10843.create_railway_announcer.announcement.*;
import com.misaka10843.create_railway_announcer.playback.*;
import com.misaka10843.create_railway_announcer.sequence.*;
import com.misaka10843.create_railway_signage.model.*;
import com.sun.net.httpserver.HttpServer;
import com.google.gson.JsonParser;
import net.minecraft.core.*;
import net.minecraft.resources.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.Level;
import net.minecraft.network.*;
import io.netty.buffer.Unpooled;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

public class AnnouncerProbe {
    private static int checks;
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[0]);
        if (args.length > 1 && args[1].equals("--live-http")) {
            var provider = new HttpTtsProvider(new HttpTtsProvider.Settings("https://tts.wangwangit.com/v1/audio/speech","","0Hz",30,true));
            var request = new TtsRequest(TtsBackend.HTTP,"ja-JP","ja-JP-NanamiNeural",.85,95,"次は、秋葉原、秋葉原です。",false,output.resolve("production-live.audio"));
            var result = provider.synthesize(request).get(40,TimeUnit.SECONDS);
            require(result.ok(),"Production Java TTS failed: "+result.error());
            var converted = new JaveOggEncoder().encodeToOgg(result.output(),output.resolve("production-live.ogg"));
            require(converted.ok(),"Production Java audio conversion failed: "+converted.error());
            System.out.println("PASS: production Java provider received "+Files.size(result.output())+" bytes; OGG duration="+OggDurationReader.durationMs(output.resolve("production-live.ogg"))+" ms");
            return;
        }
        String raw = "秋葉原 & <駅>";
        var request = new TtsRequest(TtsBackend.HTTP, "ja-JP", "ja-JP-NanamiNeural", .85, 95, raw, false, output.resolve("mock.mp3"));
        String ssml = Ssml.document(request, "0Hz");
        require(ssml.contains("rate=\"-15%\""), "Speed lost from SSML");
        require(ssml.contains("秋葉原 &amp; &lt;駅&gt;"), "Text escaped incorrectly");
        var complete = new TtsRequest(TtsBackend.HTTP, "", "", 1, 100, ssml, true, request.output());
        require(Ssml.document(complete, "20Hz").equals(ssml), "Complete SSML was rewritten");
        rejected("<speak/>", "Missing synthesis namespace accepted");
        rejected("<speak xmlns=\"http://www.w3.org/2001/10/synthesis\">", "Malformed XML accepted");
        rejected("<!DOCTYPE speak [<!ENTITY x \"injected\">]><speak xmlns=\"http://www.w3.org/2001/10/synthesis\">&x;</speak>", "DOCTYPE accepted");
        dataChecks();
        httpChecks(request, ssml);
        audioChecks(output);
        playbackChecks();
        Path live = output.resolve("live.mp3");
        if (Files.isRegularFile(live)) {
            var result = new JaveOggEncoder().encodeToOgg(live, output.resolve("live.ogg"));
            require(result.ok(), "Actual MP3 to OGG conversion failed: " + result.error());
            require(OggDurationReader.durationMs(output.resolve("live.ogg")) > 0, "Converted OGG has no duration");
        }
        System.out.println("PASS: " + checks + " actual SSML, station NBT, packet, HTTP and audio checks");
    }
    private static void playbackChecks() throws Exception {
        var backend = new ProbeBackend();
        var sequence = new ResolvedSequence("probe", "test", AudioChannel.ONBOARD_VOICE, 10, List.of());
        long baseline = schedulerThreads();
        for (int i = 0; i < 20; i++) {
            require(new PlaybackScheduler().play(sequence, backend).get(1, TimeUnit.SECONDS) == PlaybackState.FINISHED, "Playback did not finish");
        }
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (schedulerThreads() > baseline && System.nanoTime() < deadline) Thread.sleep(10);
        require(schedulerThreads() <= baseline, "Playback scheduler threads leaked after completion");
        int starts = backend.starts;
        var cancelled = new PlaybackScheduler();
        cancelled.stop();
        require(cancelled.play(sequence, backend).get(1, TimeUnit.SECONDS) == PlaybackState.STOPPED, "Pre-cancelled playback restarted");
        require(backend.starts == starts, "Pre-cancelled playback invoked the backend");
        var paused = new PlaybackScheduler();
        var waitingBackend = new ProbeBackend();
        var pause = new ResolvedSequence("pause", "test", AudioChannel.ONBOARD_VOICE, 10, List.of(ResolvedSequenceItem.pause(5000)));
        var future = paused.play(pause, waitingBackend);
        require(waitingBackend.started.await(1, TimeUnit.SECONDS), "Paused playback did not start");
        paused.stop();
        require(future.get(1, TimeUnit.SECONDS) == PlaybackState.STOPPED, "Long pause did not cancel promptly");
        var blocked = new PlaybackScheduler();
        var audioBackend = new ProbeBackend();
        audioBackend.block = true;
        var audio = new ResolvedSequence("audio", "test", AudioChannel.ONBOARD_VOICE, 10, List.of(ResolvedSequenceItem.audio(Path.of("unused.ogg"), 1000)));
        future = blocked.play(audio, audioBackend);
        require(audioBackend.playing.await(1, TimeUnit.SECONDS), "Audio playback did not start");
        blocked.stop();
        require(future.get(1, TimeUnit.SECONDS) == PlaybackState.STOPPED, "Waiting for audio could not be interrupted");
    }
    private static long schedulerThreads() {
        return Thread.getAllStackTraces().keySet().stream().filter(thread -> thread.isAlive()
                && thread.getName().equals("Create Railway Announcer Playback Scheduler")).count();
    }
    private static final class ProbeBackend implements SequenceAudioBackend {
        int starts;
        boolean block;
        final CountDownLatch started = new CountDownLatch(1), playing = new CountDownLatch(1);
        public String id() { return "test"; }
        public void onSequenceStart(ResolvedSequence sequence) { starts++; started.countDown(); }
        public void onSequenceEnd(ResolvedSequence sequence, PlaybackState state) {}
        public CompletableFuture<Void> playAudio(ResolvedSequence sequence, ResolvedSequenceItem item) {
            playing.countDown();
            return block ? new CompletableFuture<>() : CompletableFuture.completedFuture(null);
        }
        public CompletableFuture<Void> playSound(ResolvedSequence sequence, ResolvedSequenceItem item) { return playAudio(sequence, item); }
        public void showSubtitle(ResolvedSequence sequence, ResolvedSequenceItem item) {}
    }
    private static void audioChecks(Path output) throws Exception {
        int sampleRate = 24000, samples = sampleRate / 2;
        var buffer = java.nio.ByteBuffer.allocate(44 + samples * 2).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        buffer.put("RIFF".getBytes()).putInt(36 + samples * 2).put("WAVEfmt ".getBytes()).putInt(16)
                .putShort((short) 1).putShort((short) 1).putInt(sampleRate).putInt(sampleRate * 2)
                .putShort((short) 2).putShort((short) 16).put("data".getBytes()).putInt(samples * 2);
        for (int i = 0; i < samples; i++) buffer.putShort((short) (4000 * Math.sin(2 * Math.PI * 440 * i / sampleRate)));
        Path wav = output.resolve("tone.wav"), mp3 = output.resolve("tone.mp3"), ogg = output.resolve("tone.ogg");
        Files.write(wav, buffer.array());
        var audio = new ws.schild.jave.encode.AudioAttributes();
        audio.setCodec("libmp3lame"); audio.setSamplingRate(sampleRate); audio.setBitRate(48000);
        var encoding = new ws.schild.jave.encode.EncodingAttributes();
        encoding.setOutputFormat("mp3"); encoding.setAudioAttributes(audio);
        new ws.schild.jave.Encoder().encode(new ws.schild.jave.MultimediaObject(wav.toFile()), mp3.toFile(), encoding);
        var converted = new JaveOggEncoder().encodeToOgg(mp3, ogg);
        require(converted.ok(), "MP3 to OGG failed: " + converted.error());
        require(OggDurationReader.durationMs(ogg) >= 400, "Converted audio duration is invalid");
        var bytes = Files.readAllBytes(ogg);
        require(bytes[0] == 'O' && bytes[1] == 'g' && bytes[2] == 'g' && bytes[3] == 'S', "Audio is not an OGG container");
    }
    private static void rejected(String xml, String message) {
        boolean rejected = false;
        try { Ssml.validate(xml); } catch (IllegalArgumentException expected) { rejected = true; }
        require(rejected, message);
    }
    private static void dataChecks() {
        var dimension = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse("minecraft:overworld"));
        var station = new StationGroup("akihabara", "秋葉原", "Akihabara", "秋叶原");
        station.putPlatform(new PlatformBinding("1", UUID.randomUUID(), "秋葉原 1", dimension, new BlockPos(4, 64, 5), "sobu", "", "", ""));
        station.putPlatform(new PlatformBinding("2", UUID.randomUUID(), "秋葉原 2", dimension, new BlockPos(8, 64, 5), "hibiya", "", "", ""));
        var profile=com.misaka10843.create_railway_announcer.model.StationAnnouncementSettings.DEFAULT.withReadingAndDoor("あきはばら","1",DoorSide.LEFT).withReadingAndDoor("あきはばら","2",DoorSide.RIGHT);
        var restored = StationGroup.load(station.save());
        require(restored.japaneseName().equals("秋葉原") && restored.romanizedName().equals("Akihabara"), "Station names lost in NBT");
        var restoredProfile=com.misaka10843.create_railway_announcer.model.StationAnnouncementSettings.load(profile.save());
        require(restoredProfile.reading().equals("あきはばら"), "Announcement reading lost in NBT");
        require(restoredProfile.door("1") == DoorSide.LEFT && restoredProfile.door("2") == DoorSide.RIGHT, "Platform door sides collapsed");
        var line = new RailwayLine("sobu", "総武線", "Sobu Line", "", "JB", "JR East", 0xffdd00, 0);
        StationLineConfigStore.apply(List.of(restored), List.of(line),Map.of(restored.id(),restoredProfile));
        var first = StationLineConfigStore.station("akihabara/1").orElseThrow();
        require(first.getCreateStationId().equals(station.platform("1").orElseThrow().createStationId()), "Platform binding UUID lost");
        require(first.getDisplay().get("ja_jp").equals("秋葉原") && first.getReading().get("ja_jp").equals("あきはばら"), "Reading overwrote station name");
        require(first.getTransferLineIds().equals(List.of("hibiya")), "Own line announced as a transfer");
        var save = new ControllerAudioPayload.Save(BlockPos.ZERO,"akihabara",restoredProfile,false);
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            ControllerAudioPayload.Save.STREAM_CODEC.encode(buffer, save);
            require(ControllerAudioPayload.Save.STREAM_CODEC.decode(buffer).equals(save) && buffer.readableBytes() == 0, "Announcement settings packet round trip");
        } finally { buffer.release(); }
        var packet = new AnnouncementPacket(UUID.randomUUID(), AnnouncementEventType.ONBOARD_NEXT_STOP, "onboard_next_stop_dynamic",
                UUID.randomUUID(), "中央・総武線", UUID.randomUUID(), new BlockPos(4, 64, 5),
                "akihabara/1", "akihabara/2", "", 64, 24, AudioChannel.ONBOARD_VOICE, 70, 100, 105, true);
        var registryBuffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            AnnouncementPacket.STREAM_CODEC.encode(registryBuffer, packet);
            require(AnnouncementPacket.STREAM_CODEC.decode(registryBuffer).equals(packet) && registryBuffer.readableBytes() == 0, "Announcement packet round trip");
        } finally { registryBuffer.release(); }
    }
    private static void httpChecks(TtsRequest request, String ssml) throws Exception {
        var serviceUri = URI.create("https://tts.wangwangit.com/v1/audio/speech");
        var serviceBuilder = java.net.http.HttpRequest.newBuilder(serviceUri);
        HttpTtsProvider.applyServiceHeaders(serviceUri, serviceBuilder);
        var headers = serviceBuilder.GET().build().headers();
        require(headers.firstValue("Referer").orElse("").equals("https://tts.wangwangit.com/"), "Missing service Referer");
        require(headers.firstValue("Origin").orElse("").equals("https://tts.wangwangit.com"), "Missing service Origin");
        require(headers.firstValue("User-Agent").orElse("").equals("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/156.0.0.0 Safari/537.36 Edg/156.0.0.0"), "Wrong service User-Agent");
        var customUri = URI.create("https://example.com/v1/audio/speech");
        var customBuilder = java.net.http.HttpRequest.newBuilder(customUri);
        HttpTtsProvider.applyServiceHeaders(customUri, customBuilder);
        require(customBuilder.GET().build().headers().map().isEmpty(), "Service headers leaked to another host");
        AtomicReference<String> received = new AtomicReference<>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var handlers = Executors.newCachedThreadPool(task -> { var thread = new Thread(task, "TTS probe HTTP"); thread.setDaemon(true); return thread; });
        server.setExecutor(handlers);
        var holdEntered = new CountDownLatch(2);
        var releaseHold = new CountDownLatch(1);
        server.createContext("/hold", exchange -> {
            holdEntered.countDown();
            try { releaseHold.await(5, TimeUnit.SECONDS); }
            catch (InterruptedException error) { Thread.currentThread().interrupt(); }
            byte[] bytes = new byte[]{'I', 'D', '3', 4, 0, 0, 0, 0};
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes); exchange.close();
        });
        server.createContext("/speech", exchange -> {
            received.set(new String(exchange.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            byte[] bytes = new byte[]{'I', 'D', '3', 4, 0, 0, 0, 0};
            exchange.getResponseHeaders().set("Content-Type", "audio/mpeg");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.createContext("/error", exchange -> { exchange.sendResponseHeaders(429, -1); exchange.close(); });
        server.createContext("/reset", exchange -> exchange.close());
        server.createContext("/html", exchange -> {
            byte[] bytes = "<html>error</html>".getBytes();
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes); exchange.close();
        });
        server.start();
        try {
            String endpoint = "http://127.0.0.1:" + server.getAddress().getPort();
            var provider = new HttpTtsProvider(new HttpTtsProvider.Settings(endpoint + "/speech", "", "0Hz", 3, true));
            var result = provider.synthesize(request).get(5, TimeUnit.SECONDS);
            require(result.ok() && Files.size(request.output()) == 8, "Audio response not stored");
            var json = JsonParser.parseString(received.get()).getAsJsonObject();
            require(json.get("format").getAsString().equals("ssml") && json.get("ssml").getAsString().equals(ssml), "Wrong SSML HTTP contract");
            var failure = new HttpTtsProvider(new HttpTtsProvider.Settings(endpoint + "/error", "", "0Hz", 3, true)).synthesize(request).get(5, TimeUnit.SECONDS);
            require(!failure.ok() && failure.error().contains("429"), "HTTP error accepted as audio");
            failure = new HttpTtsProvider(new HttpTtsProvider.Settings(endpoint + "/html", "", "0Hz", 3, true)).synthesize(request).get(5, TimeUnit.SECONDS);
            require(!failure.ok(), "HTML accepted as audio");
            failure = new HttpTtsProvider(new HttpTtsProvider.Settings(endpoint + "/reset", "", "0Hz", 3, true)).synthesize(request).get(5, TimeUnit.SECONDS);
            require(!failure.ok() && !failure.error().contains("ExecutionException") && failure.error().contains("IOException"),"Network error did not expose its underlying cause");
            var changed = new HttpTtsProvider(new HttpTtsProvider.Settings(endpoint + "/speech", "", "+10Hz", 3, true));
            require(!changed.cacheIdentity().equals(provider.cacheIdentity()), "Pitch omitted from provider cache identity");
            var heldProvider = new HttpTtsProvider(new HttpTtsProvider.Settings(endpoint + "/hold", "", "0Hz", 10, true));
            var held = List.of(heldProvider.synthesize(request), heldProvider.synthesize(request));
            require(holdEntered.await(3, TimeUnit.SECONDS), "Both synthesis workers did not start");
            var invalid = new TtsRequest(TtsBackend.HTTP, "ja-JP", "ja-JP-NanamiNeural", 1, 100, "<speak>", true, request.output());
            var queued = new ArrayList<CompletableFuture<TtsResult>>();
            for (int i = 0; i < 64; i++) queued.add(heldProvider.synthesize(invalid));
            TtsResult rejected;
            try { rejected = heldProvider.synthesize(request).get(500, TimeUnit.MILLISECONDS); }
            catch (TimeoutException error) { throw new AssertionError("Saturated synthesis queue did not reject promptly", error); }
            require(!rejected.ok() && rejected.error().contains("queue is full"), "Saturated synthesis queue did not reject promptly");
            releaseHold.countDown();
            CompletableFuture.allOf(queued.toArray(CompletableFuture[]::new)).get(5, TimeUnit.SECONDS);
            for (var future : held) require(future.get(5, TimeUnit.SECONDS).ok(), "Running synthesis failed after queue saturation");
        } finally { releaseHold.countDown(); server.stop(0); handlers.shutdownNow(); }
    }
}
