package com.example.dragonegg.mixin;

import com.example.dragonegg.DragonEggHeartsMod;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.item.ItemEntity;
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
        if (!DragonEggHeartsMod.isDragonEgg(itemEntity.getItem())) {
            return;
        }

        if (!DragonEggHeartsMod.isEggRestorationEnabled()) {
            return;
        }

        if (!(itemEntity.level() instanceof ServerLevel serverWorld)) {
            return;
        }

        if (itemEntity.getY() >= serverWorld.getMinY()) {
            return;
        }

        DragonEggHeartsMod.restoreEggToEndPortal(serverWorld.getServer());
        itemEntity.discard();
        ci.cancel();
    }

    @Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)
    private void dragonegghearts$protectEggFromHazardDamage(
            ServerLevel world,
            DamageSource source,
            float amount,
            CallbackInfoReturnable<Boolean> cir
    ) {
        ItemEntity itemEntity = (ItemEntity) (Object) this;
        if (!DragonEggHeartsMod.isDragonEgg(itemEntity.getItem())) {
            return;
        }

        if (!DragonEggHeartsMod.isEggRestorationEnabled()) {
            return;
        }

        boolean hazard = source.is(DamageTypes.CACTUS)
                || source.is(DamageTypes.LAVA)
                || source.is(DamageTypes.IN_FIRE)
                || source.is(DamageTypes.ON_FIRE)
                || source.is(DamageTypes.FELL_OUT_OF_WORLD);

        if (!hazard) {
            return;
        }

        DragonEggHeartsMod.restoreEggToEndPortal(world.getServer());
        itemEntity.discard();
        cir.setReturnValue(false);
    }
}
