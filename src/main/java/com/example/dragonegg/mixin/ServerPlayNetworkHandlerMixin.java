package com.example.dragonegg.mixin;

import com.example.dragonegg.DragonEggHeartsMod;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.AllayEntity;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayNetworkHandler.class)
public abstract class ServerPlayNetworkHandlerMixin {
    @Shadow
    public ServerPlayerEntity player;

    @Inject(
            method = "onPlayerInteractEntity",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/network/NetworkThreadUtils;forceMainThread(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/listener/PacketListener;Lnet/minecraft/server/world/ServerWorld;)V",
                    shift = At.Shift.AFTER
            ),
            cancellable = true
    )
    private void dragonegghearts$blockDragonEggAllayInteract(
            PlayerInteractEntityC2SPacket packet,
            CallbackInfo ci
    ) {
        if (!DragonEggHeartsMod.areAllayEggInteractionsBlocked()) {
            return;
        }

        if (!DragonEggHeartsMod.playerHasDragonEggInEitherHand(player)) {
            return;
        }

        ServerWorld world = (ServerWorld) player.getEntityWorld();
        Entity target = packet.getEntity(world);
        if (!(target instanceof AllayEntity allay)) {
            return;
        }

        DragonEggHeartsMod.stripDragonEggFromAllay(world, allay, "ServerPlayNetworkHandler.onPlayerInteractEntity");
        DragonEggHeartsMod.notifyAllayEggInteractionBlocked(player, "ServerPlayNetworkHandler.onPlayerInteractEntity");
        DragonEggHeartsMod.syncBlockedAllayInteraction(player, allay, "ServerPlayNetworkHandler.onPlayerInteractEntity");
        ci.cancel();
    }
}
