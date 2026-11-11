package com.misaka10843.create_railway_announcer.sequence;

import com.google.gson.*;
import java.util.*;

public final class SequenceConditions {
    private SequenceConditions(){}
    public static List<SequenceItem> expand(List<SequenceItem> items,AnnouncementResolveContext context){return expand(items,context,0);}
    private static List<SequenceItem> expand(List<SequenceItem> items,AnnouncementResolveContext context,int depth){
        if(depth>8)throw new IllegalArgumentException("Condition nesting exceeds eight levels");
        var result=new ArrayList<SequenceItem>();
        for(var item:items){
            if(item.type()!=SequenceItemType.CONDITION){result.add(item);continue;}
            var raw=item.raw();if(!raw.has("when")||!raw.get("when").isJsonObject())throw new IllegalArgumentException("Condition requires a when object");
            boolean matches=matches(raw.getAsJsonObject("when"),context);String branch=matches?"then":"else";
            if(!raw.has(branch))continue;if(!raw.get(branch).isJsonArray())throw new IllegalArgumentException("Condition branch must be an array");
            var nested=new ArrayList<SequenceItem>();for(var value:raw.getAsJsonArray(branch)){
                var child=value.getAsJsonObject();nested.add(new SequenceItem(SequenceItemType.fromString(child.get("type").getAsString()),child));
            }
            result.addAll(expand(nested,context,depth+1));
        }
        if(result.size()>512)throw new IllegalArgumentException("Sequence exceeds 512 items");return List.copyOf(result);
    }
    private static boolean matches(JsonObject when,AnnouncementResolveContext context){
        if(when.isEmpty())throw new IllegalArgumentException("Empty condition");boolean match=true;
        for(var predicate:when.entrySet()){
            boolean value=switch(predicate.getKey()){
                case "has_station"->predicate.getValue().getAsBoolean()==context.hasStation();
                case "has_transfers"->predicate.getValue().getAsBoolean()==!context.transferLines().isEmpty();
                case "door_side"->context.hasStation()&&context.station().getDoorSide().name().equalsIgnoreCase(predicate.getValue().getAsString());
                case "platform"->context.hasStation()&&context.station().getPlatform().equals(predicate.getValue().getAsString());
                default->throw new IllegalArgumentException("Unknown condition: "+predicate.getKey());
            };match &= value;
        }
        return match;
    }
}
