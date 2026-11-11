package com.misaka10843.create_railway_announcer.block;

import com.misaka10843.create_railway_announcer.blockentity.RailwaySpeakerBlockEntity;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;

public final class RailwaySpeakerBlock extends BaseEntityBlock implements IWrenchable {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final BooleanProperty ENABLED = BlockStateProperties.ENABLED, POWERED = BlockStateProperties.POWERED;
    private final boolean onboard;
    public RailwaySpeakerBlock(Properties properties, boolean onboard) {
        super(properties); this.onboard = onboard;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(ENABLED, true).setValue(POWERED, false));
    }
    public boolean onboard() { return onboard; }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return simpleCodec(properties -> new RailwaySpeakerBlock(properties, onboard)); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING, ENABLED, POWERED); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) { return defaultBlockState().setValue(FACING, context.getClickedFace()).setValue(POWERED,context.getLevel().hasNeighborSignal(context.getClickedPos())); }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new RailwaySpeakerBlockEntity(pos, state); }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return com.misaka10843.create_railway_announcer.model.SpeakerGeometry.shape(state.getValue(FACING));
    }
    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity entity, ItemStack stack) {
        if (level.getBlockEntity(pos) instanceof RailwaySpeakerBlockEntity speaker) speaker.bindNearest();
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof RailwaySpeakerBlockEntity speaker) speaker.open(serverPlayer);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        if (context.getPlayer() instanceof ServerPlayer player && context.getLevel().getBlockEntity(context.getClickedPos()) instanceof RailwaySpeakerBlockEntity speaker) speaker.open(player);
        return InteractionResult.SUCCESS;
    }
    @Override protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, BlockPos from, boolean moved) {
        if (level.isClientSide) return;
        boolean powered = level.hasNeighborSignal(pos);
        if (powered == state.getValue(POWERED)) return;
        level.setBlock(pos, state.setValue(POWERED, powered), Block.UPDATE_CLIENTS);
        if (powered && state.getValue(ENABLED) && !onboard && level.getBlockEntity(pos) instanceof RailwaySpeakerBlockEntity speaker) speaker.trigger(null);
    }
}
