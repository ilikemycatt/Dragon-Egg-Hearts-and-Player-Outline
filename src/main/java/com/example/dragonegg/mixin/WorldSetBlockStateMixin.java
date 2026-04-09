package com.example.dragonegg.mixin;

import com.example.dragonegg.DragonEggHeartsConfig;
import com.example.dragonegg.DragonEggHeartsMod;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public class WorldSetBlockStateMixin {
    @Inject(
            method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
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

        if (!state.is(Blocks.DRAGON_EGG)) {
            return;
        }

        Level self = (Level) (Object) this;
        if (!(self instanceof ServerLevel serverWorld)) {
            return;
        }

        DragonEggHeartsMod.notifyEggPlaced(serverWorld, pos.immutable());
        if (DragonEggHeartsConfig.get().debugLogging) {
            System.out.println("[DragonEggHearts] Tracked dragon egg setBlockState at " + pos
                    + " in world " + serverWorld.dimension());
        }
    }
}
