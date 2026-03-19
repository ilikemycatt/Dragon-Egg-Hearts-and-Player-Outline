package com.example.dragonegg.mixin;

import com.example.dragonegg.DragonEggHeartsConfig;
import com.example.dragonegg.DragonEggHeartsMod;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.AllayEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = LivingEntity.class, priority = 2000)
public class LivingEntityMixin {
    @Inject(method = "setStackInHand", at = @At("HEAD"), cancellable = true)
    private void dragonegghearts$blockAllayDragonEggInHand(Hand hand, ItemStack stack, CallbackInfo ci) {
        if (!DragonEggHeartsMod.areAllayEggInteractionsBlocked()) {
            return;
        }

        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self instanceof AllayEntity)) {
            return;
        }

        if (!DragonEggHeartsMod.isDragonEgg(stack)) {
            return;
        }

        if (DragonEggHeartsConfig.get().debugLogging) {
            System.out.println("[DragonEggHearts][AllayBlock] prevented setStackInHand dragon egg for allay id="
                    + self.getId() + " hand=" + hand);
        }
        ci.cancel();
    }

    @Inject(method = "equipStack", at = @At("HEAD"), cancellable = true)
    private void dragonegghearts$blockAllayDragonEggEquip(EquipmentSlot slot, ItemStack stack, CallbackInfo ci) {
        if (!DragonEggHeartsMod.areAllayEggInteractionsBlocked()) {
            return;
        }

        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self instanceof AllayEntity)) {
            return;
        }

        if (!DragonEggHeartsMod.isDragonEgg(stack)) {
            return;
        }

        if (DragonEggHeartsConfig.get().debugLogging) {
            System.out.println("[DragonEggHearts][AllayBlock] prevented equipStack dragon egg for allay id="
                    + self.getId() + " slot=" + slot);
        }
        ci.cancel();
    }
}
