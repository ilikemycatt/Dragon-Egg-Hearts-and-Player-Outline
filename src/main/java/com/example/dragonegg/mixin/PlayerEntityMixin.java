package com.example.dragonegg.mixin;

import com.example.dragonegg.DragonEggHeartsMod;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public class PlayerEntityMixin {
    @Inject(method = "interactOn", at = @At("HEAD"), cancellable = true)
    private void dragonegghearts$blockDragonEggAllayInteract(
            Entity entity,
            InteractionHand hand,
            Vec3 location,
            CallbackInfoReturnable<InteractionResult> cir
    ) {
        if (!DragonEggHeartsMod.areAllayEggInteractionsBlocked()) {
            return;
        }

        Player self = (Player) (Object) this;
        if (!(entity instanceof Allay allay)) {
            return;
        }

        if (!DragonEggHeartsMod.playerHasDragonEggInEitherHand(self)) {
            return;
        }

        if (self instanceof ServerPlayer serverPlayer
                && DragonEggHeartsMod.isDragonEgg(self.getItemInHand(hand))) {
            DragonEggHeartsMod.stripDragonEggFromAllay((ServerLevel) self.level(), allay, "PlayerEntity.interact");
            DragonEggHeartsMod.notifyAllayEggInteractionBlocked(serverPlayer, "PlayerEntity.interact");
            DragonEggHeartsMod.syncBlockedAllayInteraction(serverPlayer, allay, "PlayerEntity.interact");
        }

        cir.setReturnValue(InteractionResult.FAIL);
    }
}
