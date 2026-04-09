package com.example.dragonegg.mixin;

import com.example.dragonegg.DragonEggHeartsMod;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Allay.class, priority = 2000)
public class AllayEntityMixin {
    @Inject(method = "wantsToPickUp", at = @At("HEAD"), cancellable = true)
    private void dragonegghearts$blockEggPickup(ServerLevel world, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (!DragonEggHeartsMod.areAllayEggInteractionsBlocked()) {
            return;
        }

        if (DragonEggHeartsMod.isDragonEgg(stack)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "mobInteract", at = @At("HEAD"), cancellable = true)
    private void dragonegghearts$blockDirectHandOff(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        if (!DragonEggHeartsMod.areAllayEggInteractionsBlocked()) {
            return;
        }

        if (!DragonEggHeartsMod.isDragonEgg(player.getItemInHand(hand))) {
            return;
        }

        Allay self = (Allay) (Object) this;
        if (self.level() instanceof ServerLevel serverWorld
                && player instanceof ServerPlayer serverPlayer) {
            DragonEggHeartsMod.stripDragonEggFromAllay(serverWorld, self, "AllayEntity.interactMob");
            DragonEggHeartsMod.notifyAllayEggInteractionBlocked(serverPlayer, "AllayEntity.interactMob");
            DragonEggHeartsMod.syncBlockedAllayInteraction(serverPlayer, self, "AllayEntity.interactMob");
        }
        cir.setReturnValue(InteractionResult.FAIL);
    }

    @Inject(
            method = "mobInteract",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/animal/allay/Allay;setItemInHand(Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/item/ItemStack;)V"
            ),
            cancellable = true
    )
    private void dragonegghearts$blockDragonEggTransferAtSource(
            Player player,
            InteractionHand hand,
            CallbackInfoReturnable<InteractionResult> cir
    ) {
        if (!DragonEggHeartsMod.areAllayEggInteractionsBlocked()) {
            return;
        }

        if (!DragonEggHeartsMod.isDragonEgg(player.getItemInHand(hand))) {
            return;
        }

        Allay self = (Allay) (Object) this;
        if (self.level() instanceof ServerLevel serverWorld
                && player instanceof ServerPlayer serverPlayer) {
            DragonEggHeartsMod.stripDragonEggFromAllay(serverWorld, self, "AllayEntity.interactMob.setStackInHand");
            DragonEggHeartsMod.notifyAllayEggInteractionBlocked(serverPlayer, "AllayEntity.interactMob.setStackInHand");
            DragonEggHeartsMod.syncBlockedAllayInteraction(serverPlayer, self, "AllayEntity.interactMob.setStackInHand");
        }
        cir.setReturnValue(InteractionResult.FAIL);
    }

    @Inject(method = "mobInteract", at = @At("TAIL"))
    private void dragonegghearts$stripEggAfterInteract(
            Player player,
            InteractionHand hand,
            CallbackInfoReturnable<InteractionResult> cir
    ) {
        if (!DragonEggHeartsMod.areAllayEggInteractionsBlocked()) {
            return;
        }

        Allay self = (Allay) (Object) this;
        if (!(self.level() instanceof ServerLevel serverWorld)) {
            return;
        }

        DragonEggHeartsMod.stripDragonEggFromAllay(serverWorld, self, "AllayEntity.interactMob.tail");
    }

    @Inject(method = "pickUpItem(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/item/ItemEntity;)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void dragonegghearts$blockLoot(ServerLevel world, ItemEntity itemEntity, CallbackInfo ci) {
        if (!DragonEggHeartsMod.areAllayEggInteractionsBlocked()) {
            return;
        }

        if (DragonEggHeartsMod.isDragonEgg(itemEntity.getItem())) {
            ci.cancel();
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void dragonegghearts$forceDropDragonEgg(CallbackInfo ci) {
        if (!DragonEggHeartsMod.areAllayEggInteractionsBlocked()) {
            return;
        }

        Allay self = (Allay) (Object) this;
        if (!(self.level() instanceof ServerLevel serverWorld)) {
            return;
        }

        // Fallback compatibility sweep: run every second unless an egg is visibly
        // in-hand, which keeps enforcement responsive without per-tick inventory work.
        if (self.tickCount % 20 != 0
                && !DragonEggHeartsMod.isDragonEgg(self.getMainHandItem())
                && !DragonEggHeartsMod.isDragonEgg(self.getOffhandItem())) {
            return;
        }

        DragonEggHeartsMod.stripDragonEggFromAllay(serverWorld, self, "AllayEntity.tick");
    }
}
