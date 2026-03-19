package com.example.dragonegg.mixin;

import com.example.dragonegg.DragonEggHeartsMod;
import net.minecraft.block.BlockState;
import net.minecraft.block.DragonEggBlock;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(DragonEggBlock.class)
public class DragonEggBlockMixin {
    @Redirect(
            method = "teleport",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/World;setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;I)Z"
            )
    )
    private boolean dragonegghearts$trackTeleportTarget(
            World world,
            BlockPos newPos,
            BlockState stateToSet,
            int flags,
            BlockState state,
            World originalWorld,
            BlockPos oldPos
    ) {
        boolean changed = world.setBlockState(newPos, stateToSet, flags);
        if (changed && world instanceof ServerWorld serverWorld && stateToSet.isOf(Blocks.DRAGON_EGG)) {
            DragonEggHeartsMod.notifyEggTeleported(serverWorld, oldPos, newPos);
        }
        return changed;
    }
}
