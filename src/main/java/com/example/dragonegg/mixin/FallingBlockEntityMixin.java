package com.example.dragonegg.mixin;

import com.example.dragonegg.DragonEggHeartsMod;
import net.minecraft.block.Blocks;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FallingBlockEntity.class)
public class FallingBlockEntityMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void dragonegghearts$restoreDragonEggAfterVoidFall(CallbackInfo ci) {
        FallingBlockEntity self = (FallingBlockEntity) (Object) this;
        if (!(self.getEntityWorld() instanceof ServerWorld serverWorld)) {
            return;
        }

        if (!DragonEggHeartsMod.isEggRestorationEnabled()) {
            return;
        }

        if (!self.getBlockState().isOf(Blocks.DRAGON_EGG)) {
            return;
        }

        if (self.getY() >= serverWorld.getBottomY()) {
            return;
        }

        DragonEggHeartsMod.restoreEggToEndPortal(serverWorld.getServer());
        self.discard();
        ci.cancel();
    }
}
