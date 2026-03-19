package com.example.dragonegg.mixin;

import com.example.dragonegg.DragonEggHeartsMod;
import net.minecraft.entity.passive.AllayEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.ActionResult;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = AllayEntity.class, priority = 2000)
public class AllayEntityMixin {
    @Inject(method = "canGather", at = @At("HEAD"), cancellable = true)
    private void dragonegghearts$blockEggPickup(ServerWorld world, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (!DragonEggHeartsMod.areAllayEggInteractionsBlocked()) {
            return;
        }

        if (DragonEggHeartsMod.isDragonEgg(stack)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "interactMob", at = @At("HEAD"), cancellable = true)
    private void dragonegghearts$blockDirectHandOff(PlayerEntity player, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
        if (!DragonEggHeartsMod.areAllayEggInteractionsBlocked()) {
            return;
        }

        if (!DragonEggHeartsMod.isDragonEgg(player.getStackInHand(hand))) {
            return;
        }

        AllayEntity self = (AllayEntity) (Object) this;
        if (self.getEntityWorld() instanceof ServerWorld serverWorld
                && player instanceof ServerPlayerEntity serverPlayer) {
            DragonEggHeartsMod.stripDragonEggFromAllay(serverWorld, self, "AllayEntity.interactMob");
            DragonEggHeartsMod.notifyAllayEggInteractionBlocked(serverPlayer, "AllayEntity.interactMob");
            DragonEggHeartsMod.syncBlockedAllayInteraction(serverPlayer, self, "AllayEntity.interactMob");
        }
        cir.setReturnValue(ActionResult.FAIL);
    }

    @Inject(
            method = "interactMob",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/passive/AllayEntity;setStackInHand(Lnet/minecraft/util/Hand;Lnet/minecraft/item/ItemStack;)V"
            ),
            cancellable = true
    )
    private void dragonegghearts$blockDragonEggTransferAtSource(
            PlayerEntity player,
            Hand hand,
            CallbackInfoReturnable<ActionResult> cir
    ) {
        if (!DragonEggHeartsMod.areAllayEggInteractionsBlocked()) {
            return;
        }

        if (!DragonEggHeartsMod.isDragonEgg(player.getStackInHand(hand))) {
            return;
        }

        AllayEntity self = (AllayEntity) (Object) this;
        if (self.getEntityWorld() instanceof ServerWorld serverWorld
                && player instanceof ServerPlayerEntity serverPlayer) {
            DragonEggHeartsMod.stripDragonEggFromAllay(serverWorld, self, "AllayEntity.interactMob.setStackInHand");
            DragonEggHeartsMod.notifyAllayEggInteractionBlocked(serverPlayer, "AllayEntity.interactMob.setStackInHand");
            DragonEggHeartsMod.syncBlockedAllayInteraction(serverPlayer, self, "AllayEntity.interactMob.setStackInHand");
        }
        cir.setReturnValue(ActionResult.FAIL);
    }

    @Inject(method = "interactMob", at = @At("TAIL"))
    private void dragonegghearts$stripEggAfterInteract(
            PlayerEntity player,
            Hand hand,
            CallbackInfoReturnable<ActionResult> cir
    ) {
        if (!DragonEggHeartsMod.areAllayEggInteractionsBlocked()) {
            return;
        }

        AllayEntity self = (AllayEntity) (Object) this;
        if (!(self.getEntityWorld() instanceof ServerWorld serverWorld)) {
            return;
        }

        DragonEggHeartsMod.stripDragonEggFromAllay(serverWorld, self, "AllayEntity.interactMob.tail");
    }

    @Inject(method = "loot", at = @At("HEAD"), cancellable = true, require = 0)
    private void dragonegghearts$blockLoot(ServerWorld world, ItemEntity itemEntity, CallbackInfo ci) {
        if (!DragonEggHeartsMod.areAllayEggInteractionsBlocked()) {
            return;
        }

        if (DragonEggHeartsMod.isDragonEgg(itemEntity.getStack())) {
            ci.cancel();
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void dragonegghearts$forceDropDragonEgg(CallbackInfo ci) {
        if (!DragonEggHeartsMod.areAllayEggInteractionsBlocked()) {
            return;
        }

        AllayEntity self = (AllayEntity) (Object) this;
        if (!(self.getEntityWorld() instanceof ServerWorld serverWorld)) {
            return;
        }

        // Fallback compatibility sweep: run every second unless an egg is visibly
        // in-hand, which keeps enforcement responsive without per-tick inventory work.
        if (self.age % 20 != 0
                && !DragonEggHeartsMod.isDragonEgg(self.getMainHandStack())
                && !DragonEggHeartsMod.isDragonEgg(self.getOffHandStack())) {
            return;
        }

        DragonEggHeartsMod.stripDragonEggFromAllay(serverWorld, self, "AllayEntity.tick");
    }
}
