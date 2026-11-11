package com.misaka10843.create_railway_announcer.data;

import com.google.gson.*;
import com.misaka10843.create_railway_announcer.CreateRailwayAnnouncer;
import com.misaka10843.create_railway_announcer.model.SpeakerGeometry;
import net.minecraft.data.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public final class SpeakerDataProvider implements DataProvider {
    private final Path root;
    public SpeakerDataProvider(PackOutput output){root=output.getOutputFolder();}
    private static JsonObject object(String source){return JsonParser.parseString(source).getAsJsonObject();}
    private static JsonArray vector(float x,float y,float z){var a=new JsonArray();a.add(x);a.add(y);a.add(z);return a;}
    @Override public CompletableFuture<?> run(CachedOutput output){
        var writes=new ArrayList<CompletableFuture<?>>();String id=CreateRailwayAnnouncer.MODID;
        var body=object("{\"parent\":\"minecraft:block/block\"}");var elements=new JsonArray();
        for(var p:SpeakerGeometry.parts()){
            var part=new JsonObject();part.add("from",vector(p.x1(),p.y1(),p.z1()));part.add("to",vector(p.x2(),p.y2(),p.z2()));
            var faces=new JsonObject();for(var side:List.of("north","south","east","west","up","down"))faces.add(side,object("{\"texture\":\"#"+p.texture()+"\"}"));
            part.add("faces",faces);elements.add(part);
        }
        body.add("elements",elements);save(output,writes,"assets/"+id+"/models/block/speaker/body.json",body);
        for(String name:List.of("station_speaker","onboard_speaker")){
            var model=object("{\"parent\":\""+id+":block/speaker/body\"}");var textures=new JsonObject();
            textures.addProperty("casing",name.equals("station_speaker")?"create:block/andesite_casing":"create:block/brass_casing");
            textures.addProperty("particle","#casing");textures.addProperty("grille","minecraft:block/black_concrete");textures.addProperty("indicator",name.equals("station_speaker")?"minecraft:block/lime_concrete":"minecraft:block/light_blue_concrete");model.add("textures",textures);
            save(output,writes,"assets/"+id+"/models/block/speaker/"+name+".json",model);var variants=new JsonObject();
            for(String facing:List.of("north","east","south","west","up","down")){
                var variant=object("{\"model\":\""+id+":block/speaker/"+name+"\"}");int y=switch(facing){case "east"->90;case "south"->180;case "west"->270;default->0;};
                if(y!=0)variant.addProperty("y",y);if(facing.equals("up"))variant.addProperty("x",270);if(facing.equals("down"))variant.addProperty("x",90);variants.add("facing="+facing,variant);
            }
            var state=new JsonObject();state.add("variants",variants);save(output,writes,"assets/"+id+"/blockstates/"+name+".json",state);
            save(output,writes,"assets/"+id+"/models/item/"+name+".json",object("{\"parent\":\""+id+":block/speaker/"+name+"\"}"));
            save(output,writes,"data/"+id+"/loot_table/blocks/"+name+".json",object("{\"type\":\"minecraft:block\",\"pools\":[{\"rolls\":1,\"entries\":[{\"type\":\"minecraft:item\",\"name\":\""+id+":"+name+"\"}],\"conditions\":[{\"condition\":\"minecraft:survives_explosion\"}]}]}"));
            String metal=name.equals("station_speaker")?"minecraft:iron_ingot":"create:brass_ingot";
            save(output,writes,"data/"+id+"/recipe/"+name+".json",object("{\"type\":\"minecraft:crafting_shaped\",\"category\":\"redstone\",\"pattern\":[\" I \",\"ANA\",\" R \"],\"key\":{\"I\":{\"item\":\""+metal+"\"},\"A\":{\"item\":\"create:andesite_alloy\"},\"N\":{\"item\":\"minecraft:note_block\"},\"R\":{\"item\":\"minecraft:redstone\"}},\"result\":{\"id\":\""+id+":"+name+"\",\"count\":2}}"));
        }
        return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
    }
    private void save(CachedOutput output,List<CompletableFuture<?>> writes,String name,JsonObject value){writes.add(DataProvider.saveStable(output,value,root.resolve(name)));}
    @Override public String getName(){return "Railway Announcer speakers";}
}
