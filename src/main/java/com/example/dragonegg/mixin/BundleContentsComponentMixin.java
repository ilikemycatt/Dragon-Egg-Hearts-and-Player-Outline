package com.example.dragonegg.mixin;

import com.example.dragonegg.DragonEggHeartsConfig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BundleContents.class)
public class BundleContentsComponentMixin {
    @Inject(method = "canItemBeInBundle", at = @At("HEAD"), cancellable = true)
    private static void dragonegghearts$blockDragonEggFromBundles(
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
