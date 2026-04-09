package com.example.dragonegg.mixin;

import com.example.dragonegg.DragonEggHeartsConfig;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerMenu.class)
public abstract class ScreenHandlerMixin {
    @Shadow
    @Final
    public NonNullList<Slot> slots;

    @Inject(method = "clicked", at = @At("HEAD"), cancellable = true)
    private void dragonegghearts$warnAndBlockContainerStore(
            int slotIndex,
            int button,
            ContainerInput actionType,
            Player player,
            CallbackInfo ci
    ) {
        if (DragonEggHeartsConfig.get().allowStorageInContainers) return;
        if (player.level().isClientSide()) return;
        if (!shouldBlock(slotIndex, button, actionType, player)) return;

        player.sendSystemMessage(Component.literal("Dragon Egg cannot be stored in containers."));
        ci.cancel();
    }

    private boolean shouldBlock(int slotIndex, int button, ContainerInput actionType, Player player) {
        AbstractContainerMenu handler = (AbstractContainerMenu) (Object) this;

        if (actionType == ContainerInput.PICKUP || actionType == ContainerInput.PICKUP_ALL) {
            if (!isContainerSlot(slotIndex)) return false;
            ItemStack cursor = handler.getCarried();
            return !cursor.isEmpty() && cursor.getItem() == Items.DRAGON_EGG;
        }

        if (actionType == ContainerInput.QUICK_MOVE) {
            if (!dragonegghearts$isValidSlotIndex(slotIndex)) return false;
            Slot source = this.slots.get(slotIndex);
            ItemStack sourceStack = source.getItem();
            return source.container instanceof Inventory
                    && !sourceStack.isEmpty()
                    && sourceStack.getItem() == Items.DRAGON_EGG
                    && hasContainerSlots();
        }

        if (actionType == ContainerInput.SWAP) {
            if (!isContainerSlot(slotIndex)) return false;
            if (button < 0 || button >= Inventory.getSelectionSize()) return false;
            ItemStack hotbarStack = player.getInventory().getItem(button);
            return !hotbarStack.isEmpty() && hotbarStack.getItem() == Items.DRAGON_EGG;
        }

        return false;
    }

    private boolean dragonegghearts$isValidSlotIndex(int slotIndex) {
        return slotIndex >= 0 && slotIndex < this.slots.size();
    }

    private boolean isContainerSlot(int slotIndex) {
        if (!dragonegghearts$isValidSlotIndex(slotIndex)) return false;
        Slot slot = this.slots.get(slotIndex);
        return !(slot.container instanceof Inventory);
    }

    private boolean hasContainerSlots() {
        for (Slot slot : this.slots) {
            if (!(slot.container instanceof Inventory)) return true;
        }
        return false;
    }
}
