package com.example.dragonegg.mixin;

import com.example.dragonegg.DragonEggHeartsConfig;
import com.example.dragonegg.DragonEggHeartsMod;
import net.minecraft.entity.passive.FoxEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FoxEntity.class)
public class FoxEntityMixin {
    @Inject(method = "canPickupItem", at = @At("HEAD"), cancellable = true)
    private void dragonegghearts$blockEggPickup(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (!DragonEggHeartsConfig.get().blockFoxEggPickup) {
            return;
        }

        if (DragonEggHeartsMod.isDragonEgg(stack)) {
            cir.setReturnValue(false);
        }
    }
}
