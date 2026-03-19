package com.example.dragonegg.mixin;

import com.example.dragonegg.DragonEggHeartsMod;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.AllayEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntity.class)
public class PlayerEntityMixin {
    @Inject(method = "interact", at = @At("HEAD"), cancellable = true)
    private void dragonegghearts$blockDragonEggAllayInteract(
            Entity entity,
            Hand hand,
            CallbackInfoReturnable<ActionResult> cir
    ) {
        if (!DragonEggHeartsMod.areAllayEggInteractionsBlocked()) {
            return;
        }

        PlayerEntity self = (PlayerEntity) (Object) this;
        if (!(entity instanceof AllayEntity allay)) {
            return;
        }

        if (!DragonEggHeartsMod.playerHasDragonEggInEitherHand(self)) {
            return;
        }

        if (self instanceof ServerPlayerEntity serverPlayer
                && DragonEggHeartsMod.isDragonEgg(self.getStackInHand(hand))) {
            DragonEggHeartsMod.stripDragonEggFromAllay((ServerWorld) self.getEntityWorld(), allay, "PlayerEntity.interact");
            DragonEggHeartsMod.notifyAllayEggInteractionBlocked(serverPlayer, "PlayerEntity.interact");
            DragonEggHeartsMod.syncBlockedAllayInteraction(serverPlayer, allay, "PlayerEntity.interact");
        }

        cir.setReturnValue(ActionResult.FAIL);
    }
}
