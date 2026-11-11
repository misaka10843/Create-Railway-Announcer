import com.misaka10843.create_railway_announcer.audio.*;
import com.misaka10843.create_railway_announcer.announcement.*;
import com.misaka10843.create_railway_announcer.client.runtime.AnnouncementPlaybackRequest;
import com.misaka10843.create_railway_announcer.config.DoorSide;
import com.misaka10843.create_railway_announcer.network.*;
import com.misaka10843.create_railway_signage.model.*;
import com.misaka10843.create_railway_announcer.model.*;
import com.misaka10843.create_railway_signage.network.*;
import com.misaka10843.create_railway_signage.data.RailwaySignageSavedData;
import com.google.gson.*;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import java.nio.file.*;
import java.util.*;

public class AnnouncerIntegrationProbe {
    static int checks;
    static void require(boolean value,String message) { if(!value)throw new AssertionError(message);checks++; }
    static void rejected(Runnable action,String message) {
        try { action.run(); } catch(IllegalArgumentException expected) {checks++;return;}
        throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        String ssml="<speak version=\"1.0\" xmlns=\"http://www.w3.org/2001/10/synthesis\" xml:lang=\"ja-JP\"><voice name=\"{voice}\">{station_reading}</voice></speak>";
        var profile=new StationAnnouncementSettings(false,"あきはばら","ja-JP-KeitaNeural",.9,"-2Hz",ssml,ssml,"",Map.of("1",DoorSide.LEFT,"2",DoorSide.RIGHT),
                "ja-JP-NanamiNeural",1.1,"+3Hz",ssml,ssml.replace("station_reading","station"),ssml);
        require(StationAnnouncementSettings.load(profile.save()).equals(profile),"Station announcement profile lost in NBT");
        require(StationAnnouncementSettings.DEFAULT.equals(StationAnnouncementSettings.DEFAULT.withReadingAndDoor("","1",DoorSide.NONE)),"Default door side creates a false edit");
        rejected(()->new StationAnnouncementSettings(true,"","",Double.NaN,"","","","",Map.of()),"NaN speed accepted");
        rejected(()->new StationAnnouncementSettings(true,"","",3,"","","","",Map.of()),"Invalid speed accepted");
        rejected(()->new StationAnnouncementSettings(true,"","",0,"","<speak/>","","",Map.of()),"Invalid custom SSML accepted");
        var data=new com.misaka10843.create_railway_announcer.data.AnnouncementSavedData();long revision=data.revision();data.set("akihabara",profile,false);
        require(!data.enabled() && data.revision()==revision+1,"Global switch did not persist a change");
        var loaded=com.misaka10843.create_railway_announcer.data.AnnouncementSavedData.load(data.snapshot(),null);
        require(!loaded.enabled() && loaded.station("akihabara").equals(profile),"World snapshot lost global switch or audio profile");
        for(Boolean global:new Boolean[]{null,false,true}) {
            var payload=new ControllerAudioPayload.Save(BlockPos.ZERO,"akihabara",profile,global);
            var buffer=new FriendlyByteBuf(Unpooled.buffer());
            try {ControllerAudioPayload.Save.STREAM_CODEC.encode(buffer,payload);require(ControllerAudioPayload.Save.STREAM_CODEC.decode(buffer).equals(payload) && buffer.readableBytes()==0,"Controller save audio/global update round trip");}
            finally {buffer.release();}
        }
        for(boolean admin:new boolean[]{false,true}) {
            var payload=new ControllerAudioPayload(BlockPos.ZERO,"akihabara","秋葉原",List.of(),profile,false,admin,admin);
            var buffer=new FriendlyByteBuf(Unpooled.buffer());
            try {ControllerAudioPayload.STREAM_CODEC.encode(buffer,payload);require(ControllerAudioPayload.STREAM_CODEC.decode(buffer).equals(payload) && buffer.readableBytes()==0,"Controller open audio/permissions round trip");}
            finally {buffer.release();}
        }
        for(boolean onboard:new boolean[]{false,true}) {
            var payload=new SpeakerScreenPayload(new BlockPos(8,64,5),onboard,"akihabara","1",32,12,AnnouncementEventType.PLATFORM_ARRIVAL,true,!onboard);
            var buffer=new FriendlyByteBuf(Unpooled.buffer());
            try {SpeakerScreenPayload.STREAM_CODEC.encode(buffer,payload);require(SpeakerScreenPayload.STREAM_CODEC.decode(buffer).equals(payload) && buffer.readableBytes()==0,"Speaker editor packet round trip");}
            finally {buffer.release();}
        }
        var saveSpeaker=new SpeakerScreenPayload.Save(BlockPos.ZERO,"1",64,16,AnnouncementEventType.PLATFORM_APPROACH,true);
        var buffer=new FriendlyByteBuf(Unpooled.buffer());
        try {SpeakerScreenPayload.Save.STREAM_CODEC.encode(buffer,saveSpeaker);require(SpeakerScreenPayload.Save.STREAM_CODEC.decode(buffer).equals(saveSpeaker) && buffer.readableBytes()==0,"Speaker save packet round trip");}
        finally {buffer.release();}
        var testSpeaker=new SpeakerScreenPayload.Test(BlockPos.ZERO,"1",AnnouncementEventType.PLATFORM_ARRIVAL);
        buffer=new FriendlyByteBuf(Unpooled.buffer());
        try {SpeakerScreenPayload.Test.STREAM_CODEC.encode(buffer,testSpeaker);require(SpeakerScreenPayload.Test.STREAM_CODEC.decode(buffer).equals(testSpeaker),"Speaker test packet round trip");}
        finally {buffer.release();}
        var rejected=ServerAnnouncementDispatcher.DispatchResult.rejected("No loaded enabled station speaker for this platform");
        require(((net.minecraft.network.chat.contents.TranslatableContents)rejected.feedback().getContents()).getKey().equals("announcer.create_railway_announcer.no_station_speaker"),"Missing speaker feedback is not localized");
        requestChecks();audienceChecks();cacheChecks(Path.of(args[0]));templateChecks(Path.of(args[0]));speakerGeometry();speechIsolation(profile);doorChecks();pauseChecks();featureChecks(Path.of(args[0]));
        System.out.println("PASS: "+checks+" integrated controller, cache, speaker and event checks");
    }
    static void featureChecks(Path output) throws Exception {
        var raw=JsonParser.parseString("{\"type\":\"condition\",\"when\":{\"has_station\":true,\"door_side\":\"RIGHT\"},\"then\":[{\"type\":\"pause\",\"duration_ms\":123}],\"else\":[{\"type\":\"pause\",\"duration_ms\":456}]}").getAsJsonObject();
        var condition=new com.misaka10843.create_railway_announcer.sequence.SequenceItem(com.misaka10843.create_railway_announcer.sequence.SequenceItemType.CONDITION,raw);
        var station=new com.misaka10843.create_railway_announcer.config.StationConfig();station.setDoorSide(DoorSide.RIGHT);
        var context=new com.misaka10843.create_railway_announcer.sequence.AnnouncementResolveContext(station,List.of());
        require(com.misaka10843.create_railway_announcer.sequence.SequenceConditions.expand(List.of(condition),context).getFirst().raw().get("duration_ms").getAsInt()==123,"Condition did not select true branch");
        require(com.misaka10843.create_railway_announcer.sequence.SequenceConditions.expand(List.of(condition),com.misaka10843.create_railway_announcer.sequence.AnnouncementResolveContext.empty()).getFirst().raw().get("duration_ms").getAsInt()==456,"Condition did not select false branch");
        var invalid=raw.deepCopy();invalid.getAsJsonObject("when").addProperty("unrecognized",true);
        rejected(()->com.misaka10843.create_railway_announcer.sequence.SequenceConditions.expand(List.of(new com.misaka10843.create_railway_announcer.sequence.SequenceItem(com.misaka10843.create_railway_announcer.sequence.SequenceItemType.CONDITION,invalid)),context),"Unknown condition accepted");
        var recursive=raw.deepCopy();for(int i=0;i<10;i++){var parent=raw.deepCopy();var children=new JsonArray();children.add(recursive);parent.add("then",children);recursive=parent;}
        var deep=new com.misaka10843.create_railway_announcer.sequence.SequenceItem(com.misaka10843.create_railway_announcer.sequence.SequenceItemType.CONDITION,recursive);
        rejected(()->com.misaka10843.create_railway_announcer.sequence.SequenceConditions.expand(List.of(deep),context),"Unbounded condition nesting accepted");
        String ssml="<speak version=\"1.0\" xmlns=\"http://www.w3.org/2001/10/synthesis\">Terminal</speak>";
        var settings=new StationAnnouncementSettings(true,"","",0,"","","","",Map.of(),"",0,"","","","",Map.of("onboard_terminal",ssml));
        require(StationAnnouncementSettings.load(settings.save()).eventSsml().equals(settings.eventSsml()),"Additional event SSML lost in NBT");
        var stop=new StopPlaybackPayload("station/1",AudioChannel.MELODY);var buffer=new FriendlyByteBuf(Unpooled.buffer());
        try{StopPlaybackPayload.STREAM_CODEC.encode(buffer,stop);require(StopPlaybackPayload.STREAM_CODEC.decode(buffer).equals(stop),"Stop channel lost in packet");}finally{buffer.release();}
        var packs=Files.createTempDirectory(output,"language-pack-");
        for(String relative:List.of("pack.json","templates/ja_jp.json","templates/en_us.json","templates/zh_cn.json","phrases/ja_jp.json","phrases/en_us.json","phrases/zh_cn.json")){
            var target=packs.resolve(relative);Files.createDirectories(target.getParent());
            try(var resource=AnnouncerIntegrationProbe.class.getResourceAsStream("/assets/create_railway_announcer/default_packs/jr_like_default/"+relative)){Files.write(target,Objects.requireNonNull(resource).readAllBytes());}
        }
        var pack=com.misaka10843.create_railway_announcer.pack.VoicePackLoader.loadPack(packs);
        for(String language:List.of("ja_jp","en_us","zh_cn")){
            com.misaka10843.create_railway_announcer.pack.VoicePackLoader.loadTemplates(pack,language);
            com.misaka10843.create_railway_announcer.pack.VoicePackLoader.loadPhrases(pack,language);
            for(var event:AnnouncementEventType.values()){
                var template=com.misaka10843.create_railway_announcer.pack.VoicePackManager.sequences().get(AnnouncementSequenceMapper.sequenceId(event)).orElseThrow();
                require(template.channel()==AnnouncementSequenceMapper.channel(event),"Language pack event channel mismatch");
                for(var item:template.items())if(item.type()==com.misaka10843.create_railway_announcer.sequence.SequenceItemType.TTS_RAW)com.misaka10843.create_railway_announcer.tts.Ssml.validate(item.raw().get("text").getAsString());
                com.misaka10843.create_railway_announcer.sequence.SequenceConditions.expand(template.items(),context);
            }
        }
        var wrong=JsonParser.parseString(Files.readString(packs.resolve("templates/ja_jp.json"))).getAsJsonObject();wrong.getAsJsonObject("platform_arrival_dynamic").addProperty("channel","ONBOARD_VOICE");Files.writeString(packs.resolve("templates/ja_jp.json"),wrong.toString());
        try{com.misaka10843.create_railway_announcer.pack.VoicePackLoader.loadTemplates(pack,"ja_jp");throw new AssertionError("Voice pack routed station event to onboard channel");}catch(java.io.IOException expected){checks++;}
        var template=new com.misaka10843.create_railway_announcer.sequence.SequenceTemplate("probe-stop","onboard_next_stop",AudioChannel.ONBOARD_VOICE,70,List.of(new com.misaka10843.create_railway_announcer.sequence.SequenceItem(com.misaka10843.create_railway_announcer.sequence.SequenceItemType.PAUSE,JsonParser.parseString("{\"duration_ms\":5000}").getAsJsonObject())));
        com.misaka10843.create_railway_announcer.pack.VoicePackManager.sequences().put(template);
        var runtime=new com.misaka10843.create_railway_announcer.client.runtime.ClientAnnouncementRuntime(new com.misaka10843.create_railway_announcer.sequence.SequenceResolver(null),new TestBackend());
        var session=runtime.play(new AnnouncementPlaybackRequest(AnnouncementEventType.ONBOARD_NEXT_STOP,"probe-stop","probe/1",UUID.randomUUID().toString())).get(2,java.util.concurrent.TimeUnit.SECONDS).session();
        com.misaka10843.create_railway_announcer.client.runtime.ClientAnnouncementRuntime.stopStation("probe/1",null);
        require(session.future().get(2,java.util.concurrent.TimeUnit.SECONDS)==com.misaka10843.create_railway_announcer.playback.PlaybackState.STOPPED,"Station stop left sequence running");
    }
    static void pauseChecks() throws Exception {
        var time=new java.util.concurrent.atomic.AtomicLong(100);
        var clock=new com.misaka10843.create_railway_announcer.playback.PlaybackClock(time::get);
        clock.paused(true);time.set(500);
        require(clock.now()==100,"Playback timeline advanced while paused");
        clock.paused(false);time.set(550);require(clock.now()==150,"Playback timeline skipped paused time");
        var game=com.misaka10843.create_railway_announcer.playback.PlaybackClock.GAME;
        game.paused(true);
        try {
            var sequence=new com.misaka10843.create_railway_announcer.sequence.ResolvedSequence("paused","test",AudioChannel.ONBOARD_VOICE,1,List.of(com.misaka10843.create_railway_announcer.sequence.ResolvedSequenceItem.pause(10)));
            var scheduler=new com.misaka10843.create_railway_announcer.playback.PlaybackScheduler();
            var future=scheduler.play(sequence,new TestBackend());
            Thread.sleep(80);require(!future.isDone(),"Paused scheduler consumed the sequence");
            game.paused(false);require(future.get(3,java.util.concurrent.TimeUnit.SECONDS)==com.misaka10843.create_railway_announcer.playback.PlaybackState.FINISHED,"Scheduler did not resume");
        } finally {game.paused(false);}
    }
    static final class TestBackend implements com.misaka10843.create_railway_announcer.playback.SequenceAudioBackend {
        public String id(){return "probe";}
        public void onSequenceStart(com.misaka10843.create_railway_announcer.sequence.ResolvedSequence sequence){}
        public void onSequenceEnd(com.misaka10843.create_railway_announcer.sequence.ResolvedSequence sequence,com.misaka10843.create_railway_announcer.playback.PlaybackState state){}
        public java.util.concurrent.CompletableFuture<Void> playAudio(com.misaka10843.create_railway_announcer.sequence.ResolvedSequence sequence,com.misaka10843.create_railway_announcer.sequence.ResolvedSequenceItem item){return java.util.concurrent.CompletableFuture.completedFuture(null);}
        public java.util.concurrent.CompletableFuture<Void> playSound(com.misaka10843.create_railway_announcer.sequence.ResolvedSequence sequence,com.misaka10843.create_railway_announcer.sequence.ResolvedSequenceItem item){return playAudio(sequence,item);}
        public void showSubtitle(com.misaka10843.create_railway_announcer.sequence.ResolvedSequence sequence,com.misaka10843.create_railway_announcer.sequence.ResolvedSequenceItem item){}
    }
    static void speechIsolation(StationAnnouncementSettings settings) throws Exception {
        var resolver = new com.misaka10843.create_railway_announcer.sequence.SequenceResolver(null);
        var profile=resolver.getClass().getDeclaredField("profile");profile.setAccessible(true);profile.set(resolver,settings);
        var platform=resolver.getClass().getDeclaredField("platform");platform.setAccessible(true);
        var custom=resolver.getClass().getDeclaredMethod("customSsml",com.misaka10843.create_railway_announcer.sequence.SequenceTemplate.class);custom.setAccessible(true);
        for(boolean station:new boolean[]{false,true}) {
            platform.setBoolean(resolver,station);
            for(String name:List.of("voice","speed","pitch")) {
                var getter=resolver.getClass().getDeclaredMethod(name);getter.setAccessible(true);
                Object expected=switch(name){case "voice"->station?settings.platformVoice():settings.voice();case "speed"->station?settings.platformSpeed():settings.speed();default->station?settings.platformPitch():settings.pitch();};
                require(getter.invoke(resolver).equals(expected),"Station and onboard voice settings leaked across channels");
            }
            var template=new com.misaka10843.create_railway_announcer.sequence.SequenceTemplate(station?"platform_arrival_dynamic":"onboard_arrived_dynamic","",station?AudioChannel.PLATFORM_VOICE:AudioChannel.ONBOARD_VOICE,80,List.of());
            require(custom.invoke(resolver,template).equals(station?settings.platformArrivalSsml():settings.arrivalSsml()),"Station and onboard arrival SSML leaked across channels");
        }
        require(settings.withReadingAndDoor("new","1",DoorSide.BOTH).platformArrivalSsml().equals(settings.platformArrivalSsml()),"Reading edit discarded platform audio");
        require(settings.withoutPlatform("1").platformVoice().equals(settings.platformVoice()),"Platform removal discarded platform audio");
    }
    static void doorChecks() throws Exception {
        var gate=new com.misaka10843.create_railway_announcer.compat.create.DoorChimeGate();
        require(gate.accept(true,100),"First door opening suppressed");
        require(!gate.accept(true,100) && !gate.accept(true,104),"Simultaneous doors played duplicate chimes");
        require(gate.accept(false,100),"Close event lost after opening");
        require(gate.accept(true,105) && gate.accept(false,105),"Later door cycle suppressed");
        require(new com.misaka10843.create_railway_announcer.compat.create.DoorChimeGate().accept(true,100),"Other train inherited door cooldown");
        var delivery=new com.misaka10843.create_railway_announcer.compat.create.DoorChimeGate();
        var passenger=UUID.randomUUID();var front=delivery.batch(true,100);var rear=delivery.batch(true,101);
        require(front.id().equals(rear.id()) && front.claim(passenger) && !rear.claim(passenger),"Multi-door sound sent twice to one listener");
        require(rear.claim(UUID.randomUUID()),"Rear platform listener lost chime after front door event");
        require(!delivery.batch(false,101).id().equals(front.id()) && !delivery.batch(true,105).id().equals(front.id()),"Different door cycles reused delivery identity");
        var target=new org.objectweb.asm.tree.ClassNode();
        new org.objectweb.asm.ClassReader("com.simibubi.create.content.trains.entity.CarriageContraptionEntity").accept(target,0);
        require(target.methods.stream().anyMatch(m->m.name.equals("setBlock")&&m.desc.equals("(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate$StructureBlockInfo;)V")),"Create door hook target signature changed");
        var hook=new org.objectweb.asm.tree.ClassNode();
        new org.objectweb.asm.ClassReader("com.misaka10843.create_railway_announcer.mixin.CarriageDoorChimeMixin").accept(hook,0);
        require(hook.methods.stream().filter(m->m.visibleAnnotations!=null).flatMap(m->m.visibleAnnotations.stream())
                .anyMatch(a->a.desc.equals("Lorg/spongepowered/asm/mixin/injection/Inject;") && a.values.contains("method")
                        && a.values.get(a.values.indexOf("method")+1).equals(List.of("setBlock"))),"Door mixin injection target is wrong");
        require(hook.methods.stream().flatMap(m->m.instructions==null?java.util.stream.Stream.empty():java.util.Arrays.stream(m.instructions.toArray()))
                .anyMatch(i->i instanceof org.objectweb.asm.tree.MethodInsnNode m && m.owner.endsWith("CreateDoorChimes")&&m.name.equals("changed")),"Door state hook does not reach chime adapter");
        try(var stream=AnnouncerIntegrationProbe.class.getResourceAsStream("/assets/create_railway_announcer/default_packs/jr_like_default/door_bip.ogg")) {
            require(stream!=null && new String(stream.readNBytes(4),java.nio.charset.StandardCharsets.US_ASCII).equals("OggS"),"Bundled door chime is missing or not OGG");
        }
    }
    static void requestChecks() {
        for(var event:List.of(AnnouncementEventType.ONBOARD_ARRIVED,AnnouncementEventType.PLATFORM_ARRIVAL,AnnouncementEventType.PLATFORM_DOOR_CLOSING,
                AnnouncementEventType.ONBOARD_NEXT_STOP,AnnouncementEventType.PLATFORM_APPROACH)) {
            var packet=new AnnouncementPacket(UUID.randomUUID(),event,AnnouncementSequenceMapper.sequenceId(event),UUID.randomUUID(),"Train",UUID.randomUUID(),
                    new BlockPos(1,2,3),"current/1","next/2","destination/3",32,12,AudioChannel.PLATFORM_VOICE,80,100,100,false);
            var request=AnnouncementPlaybackRequest.fromPacket(packet);
            boolean next=event==AnnouncementEventType.ONBOARD_NEXT_STOP||event==AnnouncementEventType.PLATFORM_APPROACH;
            require(request.stationId().equals(next?"next/2":"current/1"),"Announcement uses wrong event station");
            require(request.position().equals(packet.stationPos()) && request.horizontalRange()==32 && request.verticalRange()==12,"Speaker position/range lost");
        }
    }
    static void audienceChecks() {
        var train=UUID.randomUUID();
        var onboard=new com.misaka10843.create_railway_announcer.client.AnnouncementAudience(AudioChannel.ONBOARD_VOICE,train,null,0,0);
        require(onboard.accepts(BlockPos.ZERO,train),"Passenger on related train rejected");
        require(!onboard.accepts(BlockPos.ZERO,UUID.randomUUID()) && !onboard.accepts(BlockPos.ZERO,null),"Onboard speech continues after leaving related train");
        var platform=new com.misaka10843.create_railway_announcer.client.AnnouncementAudience(AudioChannel.PLATFORM_VOICE,train,new BlockPos(0,64,0),32,12);
        require(platform.accepts(new BlockPos(32,76,-32),null),"Speaker boundary rejected");
        require(!platform.accepts(new BlockPos(33,64,0),null) && !platform.accepts(new BlockPos(0,77,0),train),"Station speech continues outside speaker range");
        require(!platform.accepts(new BlockPos(Integer.MIN_VALUE,64,0),null),"Speaker distance overflow accepted");
        require(!platform.accepts(new BlockPos(1,64,0),train),"Platform audio leaked into passenger compartment");
        var unbound=new com.misaka10843.create_railway_announcer.client.AnnouncementAudience(AudioChannel.ONBOARD_VOICE,null,BlockPos.ZERO,32,12);
        require(!unbound.accepts(BlockPos.ZERO,null),"Unbound onboard audio played at station");
        var chime=new com.misaka10843.create_railway_announcer.client.AnnouncementAudience(AudioChannel.DOOR_CHIME,train,BlockPos.ZERO,24,8);
        require(chime.accepts(new BlockPos(200,0,0),train),"Door chime lost on long train");
        require(chime.accepts(new BlockPos(20,0,0),null) && !chime.accepts(new BlockPos(25,0,0),null),"Door chime exterior range is wrong");
    }
    static void cacheChecks(Path output) throws Exception {
        Path root=Files.createTempDirectory(output,"cache-integration-");
        var cache=new AudioCacheFiles(root);
        Path audio=root.resolve("a".repeat(64)+".ogg"),temp=root.resolve("tts-test.audio"),personal=root.resolve("my-recording.ogg"),directory=root.resolve("b".repeat(64)+".ogg");
        Files.write(audio,new byte[10]);Files.write(temp,new byte[3]);Files.write(personal,new byte[7]);Files.createDirectory(directory);
        var stats=cache.stats();require(stats.files()==1 && stats.bytes()==13,"Cache statistics count unowned files");
        long generation=AudioCacheFiles.epoch();var cleared=cache.clear();
        require(cleared.deleted()==2 && cleared.failed()==0 && Files.exists(personal) && Files.isDirectory(directory),"Cache clear deleted unowned data");
        require(cache.stats().files()==0,"Cache clear left generated audio");
        Path encoded=root.resolve("tts-finished.ogg"),destination=root.resolve("c".repeat(64)+".ogg");Files.write(encoded,new byte[3]);
        require(!AudioCacheFiles.publish(generation,encoded,destination) && !Files.exists(destination),"Cleared cache was repopulated by stale generation");
        require(AudioCacheFiles.publish(AudioCacheFiles.epoch(),encoded,destination),"Fresh generation cannot publish");
    }
    static void templateChecks(Path output) throws Exception {
        JsonObject templates;
        try(var stream=AnnouncerIntegrationProbe.class.getResourceAsStream("/assets/create_railway_announcer/default_packs/jr_like_default/templates/ja_jp.json")) {
            require(stream!=null,"Bundled templates are absent from runtime resources");
            templates=JsonParser.parseReader(new java.io.InputStreamReader(stream,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        }
        for(var event:List.of(AnnouncementEventType.ONBOARD_NEXT_STOP,AnnouncementEventType.ONBOARD_ARRIVED,AnnouncementEventType.ONBOARD_DEPARTING,
                AnnouncementEventType.DOOR_CLOSING,AnnouncementEventType.PLATFORM_APPROACH,AnnouncementEventType.PLATFORM_ARRIVAL,
                AnnouncementEventType.PLATFORM_DOOR_CLOSING,AnnouncementEventType.PLATFORM_PRE_DEPARTURE,AnnouncementEventType.PLATFORM_DEPARTURE_MELODY))
            require(templates.has(AnnouncementSequenceMapper.sequenceId(event)),"Dispatched event has no bundled template");
        Path root=Files.createTempDirectory(output,"pack-integration-");Files.createDirectories(root.resolve("templates"));
        Files.writeString(root.resolve("templates/ja_jp.json"),"{\"onboard_next_stop_dynamic\":{\"custom\":true}}");
        var merge=com.misaka10843.create_railway_announcer.pack.VoicePackLoader.class.getDeclaredMethod("mergeMissingTemplates",Path.class);merge.setAccessible(true);merge.invoke(null,root);
        var merged=JsonParser.parseString(Files.readString(root.resolve("templates/ja_jp.json"))).getAsJsonObject();
        require(merged.has("platform_arrival_dynamic") && merged.getAsJsonObject("onboard_next_stop_dynamic").get("custom").getAsBoolean(),"Template update overwrote customization or omitted new events");
        var oldArrival=templates.getAsJsonObject("onboard_arrived_dynamic").deepCopy();
        var fixedLeft=new JsonObject();fixedLeft.addProperty("type","phrase");fixedLeft.addProperty("id","common.door_left");
        oldArrival.getAsJsonArray("sequence").set(1,fixedLeft);merged.add("onboard_arrived_dynamic",oldArrival);
        var baselinePath=root.resolve(".bundled/templates/ja_jp.json");var baseline=JsonParser.parseString(Files.readString(baselinePath)).getAsJsonObject();baseline.add("onboard_arrived_dynamic",oldArrival);Files.writeString(baselinePath,baseline.toString());
        Files.writeString(root.resolve("templates/ja_jp.json"),merged.toString());merge.invoke(null,root);
        merged=JsonParser.parseString(Files.readString(root.resolve("templates/ja_jp.json"))).getAsJsonObject();
        require(merged.getAsJsonObject("onboard_arrived_dynamic").equals(templates.get("onboard_arrived_dynamic")),"Old bundled arrival still forces left doors");
        for(String id:List.of("door_open_chime","door_close_chime")) {
            var template=templates.getAsJsonObject(id);
            require(template.get("channel").getAsString().equals("DOOR_CHIME") && template.getAsJsonArray("sequence").get(0).getAsJsonObject().get("file").getAsString().equals("door_bip.ogg"),"Door chime uses speech channel or missing recording");
        }
        for(var event:AnnouncementEventType.values()) {
            String id=AnnouncementSequenceMapper.sequenceId(event);
            if(id!=null) require(templates.getAsJsonObject(id).get("channel").getAsString().equals(AnnouncementSequenceMapper.channel(event).name()),"Event is routed to wrong audio channel");
        }
    }
    static void speakerGeometry() throws Exception {
        var model=net.minecraft.client.renderer.block.model.BlockModel.fromString(Files.readString(Path.of("src/generated/resources/assets/create_railway_announcer/models/block/speaker/body.json")));
        var states=JsonParser.parseString(Files.readString(Path.of("src/generated/resources/assets/create_railway_announcer/blockstates/station_speaker.json"))).getAsJsonObject().getAsJsonObject("variants");
        require(states.size()==6,"Speaker generated state family is not six shared orientations");
        for(var direction:net.minecraft.core.Direction.values()) {
            var bounds=SpeakerGeometry.shape(direction).bounds();
            var variant=states.getAsJsonObject("facing="+direction.getName());
            int x=variant.has("x")?variant.get("x").getAsInt():0,y=variant.has("y")?variant.get("y").getAsInt():0;
            var rotation=net.minecraft.client.resources.model.BlockModelRotation.by(x,y).getRotation().getMatrix();
            float minX=Float.POSITIVE_INFINITY,minY=minX,minZ=minX,maxX=Float.NEGATIVE_INFINITY,maxY=maxX,maxZ=maxX;
            for(var element:model.getElements()) for(int i=0;i<8;i++) {
                var point=new org.joml.Vector3f((i&1)==0?element.from.x:element.to.x,(i&2)==0?element.from.y:element.to.y,(i&4)==0?element.from.z:element.to.z);
                point.div(16).sub(.5f,.5f,.5f).mulPosition(rotation).add(.5f,.5f,.5f);
                minX=Math.min(minX,point.x);maxX=Math.max(maxX,point.x);minY=Math.min(minY,point.y);maxY=Math.max(maxY,point.y);minZ=Math.min(minZ,point.z);maxZ=Math.max(maxZ,point.z);
            }
            require(Math.abs(bounds.minX-minX)<1e-5&&Math.abs(bounds.maxX-maxX)<1e-5&&Math.abs(bounds.minY-minY)<1e-5&&Math.abs(bounds.maxY-maxY)<1e-5&&Math.abs(bounds.minZ-minZ)<1e-5&&Math.abs(bounds.maxZ-maxZ)<1e-5,"Speaker model/selection bounds differ for "+direction);
        }
    }
}
