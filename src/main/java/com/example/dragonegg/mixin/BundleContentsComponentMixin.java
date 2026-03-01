package com.example.dragonegg.mixin;

import com.example.dragonegg.DragonEggHeartsConfig;
import net.minecraft.component.type.BundleContentsComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BundleContentsComponent.class)
public class BundleContentsComponentMixin {
    @Inject(method = "canBeBundled", at = @At("HEAD"), cancellable = true)
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
