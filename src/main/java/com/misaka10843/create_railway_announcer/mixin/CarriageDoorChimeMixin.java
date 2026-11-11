package com.misaka10843.create_railway_announcer.mixin;

import com.misaka10843.create_railway_announcer.compat.create.CreateDoorChimes;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = CarriageContraptionEntity.class, remap = false)
public abstract class CarriageDoorChimeMixin {
    @Inject(method = "setBlock", at = @At("HEAD"))
    private void railwaySignage$doorSound(BlockPos pos, StructureBlockInfo info, CallbackInfo ci) {
        CreateDoorChimes.changed((CarriageContraptionEntity)(Object)this,pos,info);
    }
}
