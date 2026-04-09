package com.example.dragonegg.mixin;

import com.example.dragonegg.DragonEggHeartsMod;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockItem.class)
public class BlockItemMixin {
    @Inject(method = "place", at = @At("RETURN"))
    private void dragonegghearts$announceEggPlacement(BlockPlaceContext context, CallbackInfoReturnable<InteractionResult> cir) {
        BlockItem self = (BlockItem) (Object) this;
        if (self.asItem() != Items.DRAGON_EGG || !cir.getReturnValue().consumesAction()) {
            return;
        }

        Level world = context.getLevel();
        if (world.isClientSide() || !(context.getPlayer() instanceof ServerPlayer player)) {
            return;
        }

        // Determine the actual placed dragon egg position. Depending on placement
        // logic the reported "clicked" pos can be the block clicked rather than
        // the position the egg ends up at, so check a small neighborhood around
        // the clicked position (including the offset side) and choose the one
        // that actually contains the dragon egg block.
        BlockPos clicked = context.getClickedPos();
        BlockPos placedPos = null;

        // Check clicked block and the adjacent offset first.
        if (world.getBlockState(clicked).is(Blocks.DRAGON_EGG)) {
            placedPos = clicked;
        }

        if (placedPos == null) {
            BlockPos offset = clicked.relative(context.getClickedFace());
            if (world.getBlockState(offset).is(Blocks.DRAGON_EGG)) {
                placedPos = offset;
            }
        }

        // As a final fallback, search a 1-block radius cube around the clicked
        // position for the dragon egg. This handles cases where placement
        // logic moves the egg slightly.
        if (placedPos == null) {
            for (int dx = -1; dx <= 1 && placedPos == null; dx++) {
                for (int dy = -1; dy <= 1 && placedPos == null; dy++) {
                    for (int dz = -1; dz <= 1 && placedPos == null; dz++) {
                        BlockPos check = clicked.offset(dx, dy, dz);
                        if (world.getBlockState(check).is(Blocks.DRAGON_EGG)) {
                            placedPos = check;
                        }
                    }
                }
            }
        }

        if (placedPos != null) {
            DragonEggHeartsMod.notifyEggPlaced(player, placedPos);
        } else {
            // Fallback to clicked position; verification can rebind to the
            // real egg position if it was offset.
            DragonEggHeartsMod.notifyEggPlaced(player, clicked);
        }
    }
}
