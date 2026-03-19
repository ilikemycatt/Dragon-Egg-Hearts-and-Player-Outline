package com.example.dragonegg.mixin;

import com.example.dragonegg.DragonEggHeartsMod;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.AllayEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStack.class)
public class ItemStackMixin {
    @Inject(method = "useOnEntity", at = @At("HEAD"), cancellable = true)
    private void dragonegghearts$blockEggUseOnAllay(
            PlayerEntity user,
            LivingEntity entity,
            Hand hand,
            CallbackInfoReturnable<ActionResult> cir
    ) {
        if (!DragonEggHeartsMod.areAllayEggInteractionsBlocked()) {
            return;
        }

        ItemStack self = (ItemStack) (Object) this;
        if (!DragonEggHeartsMod.isDragonEgg(self) || !(entity instanceof AllayEntity)) {
            return;
        }

        if (!user.getEntityWorld().isClient()
                && user instanceof ServerPlayerEntity serverPlayer
                && entity.getEntityWorld() instanceof ServerWorld serverWorld) {
            AllayEntity allay = (AllayEntity) entity;
            DragonEggHeartsMod.stripDragonEggFromAllay(serverWorld, allay, "ItemStack.useOnEntity");
            DragonEggHeartsMod.notifyAllayEggInteractionBlocked(serverPlayer, "ItemStack.useOnEntity");
            DragonEggHeartsMod.syncBlockedAllayInteraction(serverPlayer, allay, "ItemStack.useOnEntity");
        }
        cir.setReturnValue(ActionResult.FAIL);
    }
}
