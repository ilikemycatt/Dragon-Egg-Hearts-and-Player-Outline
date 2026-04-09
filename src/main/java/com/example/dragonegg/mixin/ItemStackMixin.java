package com.example.dragonegg.mixin;

import com.example.dragonegg.DragonEggHeartsMod;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStack.class)
public class ItemStackMixin {
    @Inject(method = "interactLivingEntity", at = @At("HEAD"), cancellable = true)
    private void dragonegghearts$blockEggUseOnAllay(
            Player user,
            LivingEntity entity,
            InteractionHand hand,
            CallbackInfoReturnable<InteractionResult> cir
    ) {
        if (!DragonEggHeartsMod.areAllayEggInteractionsBlocked()) {
            return;
        }

        ItemStack self = (ItemStack) (Object) this;
        if (!DragonEggHeartsMod.isDragonEgg(self) || !(entity instanceof Allay)) {
            return;
        }

        if (!user.level().isClientSide()
                && user instanceof ServerPlayer serverPlayer
                && entity.level() instanceof ServerLevel serverWorld) {
            Allay allay = (Allay) entity;
            DragonEggHeartsMod.stripDragonEggFromAllay(serverWorld, allay, "ItemStack.useOnEntity");
            DragonEggHeartsMod.notifyAllayEggInteractionBlocked(serverPlayer, "ItemStack.useOnEntity");
            DragonEggHeartsMod.syncBlockedAllayInteraction(serverPlayer, allay, "ItemStack.useOnEntity");
        }
        cir.setReturnValue(InteractionResult.FAIL);
    }
}
