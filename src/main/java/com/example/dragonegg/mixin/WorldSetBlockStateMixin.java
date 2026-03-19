package com.example.dragonegg.mixin;

import com.example.dragonegg.DragonEggHeartsConfig;
import com.example.dragonegg.DragonEggHeartsMod;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(World.class)
public class WorldSetBlockStateMixin {
    @Inject(
            method = "setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;II)Z",
            at = @At("RETURN")
    )
    private void dragonegghearts$trackDragonEggSet(
            BlockPos pos,
            BlockState state,
            int flags,
            int maxUpdateDepth,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (!cir.getReturnValue()) {
            return;
        }

        if (!state.isOf(Blocks.DRAGON_EGG)) {
            return;
        }

        World self = (World) (Object) this;
        if (!(self instanceof ServerWorld serverWorld)) {
            return;
        }

        DragonEggHeartsMod.notifyEggPlaced(serverWorld, pos.toImmutable());
        if (DragonEggHeartsConfig.get().debugLogging) {
            System.out.println("[DragonEggHearts] Tracked dragon egg setBlockState at " + pos
                    + " in world " + serverWorld.getRegistryKey());
        }
    }
}
