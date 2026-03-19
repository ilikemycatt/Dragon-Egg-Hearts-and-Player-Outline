package com.example.dragonegg.mixin;

import com.example.dragonegg.DragonEggHeartsConfig;
import com.example.dragonegg.DragonEggHeartsMod;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HopperBlockEntity.class)
public class HopperBlockEntityMixin {
    @Inject(method = "canInsert", at = @At("HEAD"), cancellable = true)
    private static void dragonegghearts$blockEggInsert(
            Inventory inventory,
            ItemStack stack,
            int slot,
            Direction side,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (!DragonEggHeartsConfig.get().blockHopperEggInsertion) {
            return;
        }

        if (DragonEggHeartsMod.isDragonEgg(stack)) {
            cir.setReturnValue(false);
        }
    }
}
