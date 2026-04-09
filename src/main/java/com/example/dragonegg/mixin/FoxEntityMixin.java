package com.example.dragonegg.mixin;

import com.example.dragonegg.DragonEggHeartsConfig;
import com.example.dragonegg.DragonEggHeartsMod;
import net.minecraft.world.entity.animal.fox.Fox;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Fox.class)
public class FoxEntityMixin {
    @Inject(method = "canHoldItem", at = @At("HEAD"), cancellable = true)
    private void dragonegghearts$blockEggPickup(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (!DragonEggHeartsConfig.get().blockFoxEggPickup) {
            return;
        }

        if (DragonEggHeartsMod.isDragonEgg(stack)) {
            cir.setReturnValue(false);
        }
    }
}
