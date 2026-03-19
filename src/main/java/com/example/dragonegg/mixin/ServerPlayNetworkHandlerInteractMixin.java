package com.example.dragonegg.mixin;

import com.example.dragonegg.DragonEggHeartsMod;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.AllayEntity;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.server.network.ServerPlayNetworkHandler$1")
public abstract class ServerPlayNetworkHandlerInteractMixin {
    @Shadow
    @Final
    private Entity field_28962;

    @Shadow
    @Final
    private ServerPlayNetworkHandler field_28963;

    @Inject(method = "processInteract", at = @At("HEAD"), cancellable = true, require = 0)
    private void dragonegghearts$blockDragonEggAllayInteract(CallbackInfo ci) {
        if (!DragonEggHeartsMod.areAllayEggInteractionsBlocked()) {
            return;
        }

        if (!(field_28962 instanceof AllayEntity allay)) {
            return;
        }

        ServerPlayerEntity player = field_28963.player;
        if (!DragonEggHeartsMod.playerHasDragonEggInEitherHand(player)) {
            return;
        }

        DragonEggHeartsMod.stripDragonEggFromAllay((ServerWorld) player.getEntityWorld(), allay, "ServerPlayNetworkHandler$1.processInteract");
        DragonEggHeartsMod.notifyAllayEggInteractionBlocked(player, "ServerPlayNetworkHandler$1.processInteract");
        DragonEggHeartsMod.syncBlockedAllayInteraction(player, allay, "ServerPlayNetworkHandler$1.processInteract");
        ci.cancel();
    }

    @Inject(method = "interactAt", at = @At("HEAD"), cancellable = true, require = 0)
    private void dragonegghearts$blockDragonEggAllayInteractAt(CallbackInfo ci) {
        if (!DragonEggHeartsMod.areAllayEggInteractionsBlocked()) {
            return;
        }

        if (!(field_28962 instanceof AllayEntity allay)) {
            return;
        }

        ServerPlayerEntity player = field_28963.player;
        if (!DragonEggHeartsMod.playerHasDragonEggInEitherHand(player)) {
            return;
        }

        DragonEggHeartsMod.stripDragonEggFromAllay((ServerWorld) player.getEntityWorld(), allay, "ServerPlayNetworkHandler$1.processInteractAt");
        DragonEggHeartsMod.notifyAllayEggInteractionBlocked(player, "ServerPlayNetworkHandler$1.processInteractAt");
        DragonEggHeartsMod.syncBlockedAllayInteraction(player, allay, "ServerPlayNetworkHandler$1.processInteractAt");
        ci.cancel();
    }
}
