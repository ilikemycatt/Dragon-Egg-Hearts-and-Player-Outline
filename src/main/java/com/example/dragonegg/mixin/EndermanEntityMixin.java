package com.example.dragonegg.mixin;

import com.example.dragonegg.DragonEggHeartsConfig;
import com.example.dragonegg.DragonEggHeartsMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EnderMan.class)
public class EndermanEntityMixin {
    @Inject(method = "isBeingStaredBy", at = @At("HEAD"), cancellable = true)
    private static void dragonegghearts$ignoreStareForEggCarrier(Player player, CallbackInfoReturnable<Boolean> cir) {
        if (!DragonEggHeartsConfig.get().endermanIgnoreStareForEggCarriers) {
            return;
        }

        if (player instanceof ServerPlayer serverPlayer && DragonEggHeartsMod.isEggCarrierCached(serverPlayer)) {
            cir.setReturnValue(false);
        }
    }
}
