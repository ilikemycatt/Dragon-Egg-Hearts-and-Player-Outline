package com.example.dragonegg.mixin;

import com.example.dragonegg.DragonEggHeartsConfig;
import net.minecraft.world.inventory.ShulkerBoxSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ShulkerBoxSlot.class)
public class ShulkerBoxSlotMixin {
    @Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
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
