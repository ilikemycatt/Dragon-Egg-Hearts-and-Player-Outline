package com.example.dragonegg.mixin;

import com.example.dragonegg.DragonEggHeartsMod;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.allay.Allay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerPlayNetworkHandlerMixin {
    @Shadow
    public ServerPlayer player;

    @Inject(
            method = "handleInteract",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V",
                    shift = At.Shift.AFTER
            ),
            cancellable = true
    )
    private void dragonegghearts$blockDragonEggAllayInteract(
            ServerboundInteractPacket packet,
            CallbackInfo ci
    ) {
        if (!DragonEggHeartsMod.areAllayEggInteractionsBlocked()) {
            return;
        }

        if (!DragonEggHeartsMod.playerHasDragonEggInEitherHand(player)) {
            return;
        }

        ServerLevel world = (ServerLevel) player.level();
        Entity target = world.getEntityOrPart(packet.entityId());
        if (!(target instanceof Allay allay)) {
            return;
        }

        DragonEggHeartsMod.stripDragonEggFromAllay(world, allay, "ServerPlayNetworkHandler.onPlayerInteractEntity");
        DragonEggHeartsMod.notifyAllayEggInteractionBlocked(player, "ServerPlayNetworkHandler.onPlayerInteractEntity");
        DragonEggHeartsMod.syncBlockedAllayInteraction(player, allay, "ServerPlayNetworkHandler.onPlayerInteractEntity");
        ci.cancel();
    }
}
