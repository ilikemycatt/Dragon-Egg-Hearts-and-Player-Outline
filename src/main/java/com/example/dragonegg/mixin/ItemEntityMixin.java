package com.example.dragonegg.mixin;

import com.example.dragonegg.DragonEggHeartsMod;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemEntity.class)
public class ItemEntityMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void dragonegghearts$restoreEggWhenBelowWorld(CallbackInfo ci) {
        ItemEntity itemEntity = (ItemEntity) (Object) this;
        if (!DragonEggHeartsMod.isDragonEgg(itemEntity.getStack())) {
            return;
        }

        if (!DragonEggHeartsMod.isEggRestorationEnabled()) {
            return;
        }

        if (!(itemEntity.getEntityWorld() instanceof ServerWorld serverWorld)) {
            return;
        }

        if (itemEntity.getY() >= serverWorld.getBottomY()) {
            return;
        }

        DragonEggHeartsMod.restoreEggToEndPortal(serverWorld.getServer());
        itemEntity.discard();
        ci.cancel();
    }

    @Inject(method = "damage", at = @At("HEAD"), cancellable = true)
    private void dragonegghearts$protectEggFromHazardDamage(
            ServerWorld world,
            DamageSource source,
            float amount,
            CallbackInfoReturnable<Boolean> cir
    ) {
        ItemEntity itemEntity = (ItemEntity) (Object) this;
        if (!DragonEggHeartsMod.isDragonEgg(itemEntity.getStack())) {
            return;
        }

        if (!DragonEggHeartsMod.isEggRestorationEnabled()) {
            return;
        }

        boolean hazard = source.isOf(DamageTypes.CACTUS)
                || source.isOf(DamageTypes.LAVA)
                || source.isOf(DamageTypes.IN_FIRE)
                || source.isOf(DamageTypes.ON_FIRE)
                || source.isOf(DamageTypes.OUT_OF_WORLD);

        if (!hazard) {
            return;
        }

        DragonEggHeartsMod.restoreEggToEndPortal(world.getServer());
        itemEntity.discard();
        cir.setReturnValue(false);
    }
}
