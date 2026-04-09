package com.example.dragonegg.mixin;

import com.example.dragonegg.DragonEggHeartsMod;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DragonEggBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(DragonEggBlock.class)
public class DragonEggBlockMixin {
    @Redirect(
            method = "teleport",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"
            )
    )
    private boolean dragonegghearts$trackTeleportTarget(
            Level world,
            BlockPos newPos,
            BlockState stateToSet,
            int flags,
            BlockState state,
            Level originalWorld,
            BlockPos oldPos
    ) {
        boolean changed = world.setBlock(newPos, stateToSet, flags);
        if (changed && world instanceof ServerLevel serverWorld && stateToSet.is(Blocks.DRAGON_EGG)) {
            DragonEggHeartsMod.notifyEggTeleported(serverWorld, oldPos, newPos);
        }
        return changed;
    }
}
