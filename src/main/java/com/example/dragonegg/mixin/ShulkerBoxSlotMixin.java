package com.example.dragonegg.mixin;

import com.example.dragonegg.DragonEggHeartsConfig;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.ShulkerBoxSlot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ShulkerBoxSlot.class)
public class ShulkerBoxSlotMixin {
    @Inject(method = "canInsert", at = @At("HEAD"), cancellable = true)
    private void dragonegghearts$blockDragonEggFromShulkers(
            ItemStack stack,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (DragonEggHeartsConfig.get().allowStorageInContainers) {
            return;
        }

        if (!stack.isEmpty() && stack.getItem() == Items.DRAGON_EGG) {
            cir.setReturnValue(false);
        }
    }
}
