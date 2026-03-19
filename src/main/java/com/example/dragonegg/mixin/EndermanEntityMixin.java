package com.example.dragonegg.mixin;

import com.example.dragonegg.DragonEggHeartsConfig;
import com.example.dragonegg.DragonEggHeartsMod;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EndermanEntity.class)
public class EndermanEntityMixin {
    @Inject(method = "isPlayerStaring", at = @At("HEAD"), cancellable = true)
    private static void dragonegghearts$ignoreStareForEggCarrier(PlayerEntity player, CallbackInfoReturnable<Boolean> cir) {
        if (!DragonEggHeartsConfig.get().endermanIgnoreStareForEggCarriers) {
            return;
        }

        if (player instanceof ServerPlayerEntity serverPlayer && DragonEggHeartsMod.isEggCarrierCached(serverPlayer)) {
            cir.setReturnValue(false);
        }
    }
}
