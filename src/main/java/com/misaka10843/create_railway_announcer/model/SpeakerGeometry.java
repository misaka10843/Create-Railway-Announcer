package com.misaka10843.create_railway_announcer.model;

import com.misaka10843.create_railway_signage.block.BlockShapeRotation;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.*;
import java.util.*;

public final class SpeakerGeometry {
    public record Part(String texture, float x1,float y1,float z1,float x2,float y2,float z2) {}
    private static final List<Part> PARTS = partsList();
    private static final Map<Direction,VoxelShape> SHAPES = shapes();
    private SpeakerGeometry() {}
    private static List<Part> partsList() {
        List<Part> parts = new ArrayList<>();
        parts.add(new Part("casing",3,4,10,13,12,16));
        parts.add(new Part("grille",4,5,9.5f,12,11,10));
        for(int y=6;y<=10;y+=2)parts.add(new Part("casing",4,y,9.4f,12,y+.5f,9.5f));
        parts.add(new Part("indicator",11.5f,4.25f,9.9f,12.5f,4.75f,10));
        return List.copyOf(parts);
    }
    private static Map<Direction,VoxelShape> shapes() {
        VoxelShape north=Shapes.empty();
        for(var part:PARTS)north=Shapes.or(north,Shapes.create(part.x1/16d,part.y1/16d,part.z1/16d,part.x2/16d,part.y2/16d,part.z2/16d));
        Map<Direction,VoxelShape> shapes=new EnumMap<>(Direction.class);
        for(var direction:Direction.Plane.HORIZONTAL)shapes.put(direction,BlockShapeRotation.rotate(north,BlockShapeRotation.quarterTurnsFromNorth(direction)));
        VoxelShape up=Shapes.empty(),down=Shapes.empty();
        for(var box:north.toAabbs()) {
            up=Shapes.or(up,Shapes.create(box.minX,1-box.maxZ,box.minY,box.maxX,1-box.minZ,box.maxY));
            down=Shapes.or(down,Shapes.create(box.minX,box.minZ,1-box.maxY,box.maxX,box.maxZ,1-box.minY));
        }
        shapes.put(Direction.UP,up);shapes.put(Direction.DOWN,down);
        return Collections.unmodifiableMap(shapes);
    }
    public static List<Part> parts() { return PARTS; }
    public static VoxelShape shape(Direction facing) { return SHAPES.get(facing); }
}
