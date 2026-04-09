package com.example.dragonegg.mixin;

import com.example.dragonegg.DragonEggHeartsMod;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.Blocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FallingBlockEntity.class)
public class FallingBlockEntityMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void dragonegghearts$restoreDragonEggAfterVoidFall(CallbackInfo ci) {
        FallingBlockEntity self = (FallingBlockEntity) (Object) this;
        if (!(self.level() instanceof ServerLevel serverWorld)) {
            return;
        }

        if (!DragonEggHeartsMod.isEggRestorationEnabled()) {
            return;
        }

        if (!self.getBlockState().is(Blocks.DRAGON_EGG)) {
            return;
        }

        if (self.getY() >= serverWorld.getMinY()) {
            return;
        }

        DragonEggHeartsMod.restoreEggToEndPortal(serverWorld.getServer());
        self.discard();
        ci.cancel();
    }
}
