package com.example.dragonegg.mixin;

import com.example.dragonegg.DragonEggHeartsConfig;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ScreenHandler.class)
public abstract class ScreenHandlerMixin {
    @Shadow
    @Final
    public DefaultedList<Slot> slots;

    @Inject(method = "onSlotClick", at = @At("HEAD"), cancellable = true)
    private void dragonegghearts$warnAndBlockContainerStore(
            int slotIndex,
            int button,
            SlotActionType actionType,
            PlayerEntity player,
            CallbackInfo ci
    ) {
        if (DragonEggHeartsConfig.get().allowStorageInContainers) return;
        if (player.getEntityWorld().isClient()) return;
        if (!shouldBlock(slotIndex, button, actionType, player)) return;

        player.sendMessage(Text.literal("Dragon Egg cannot be stored in containers."), false);
        ci.cancel();
    }

    private boolean shouldBlock(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
        ScreenHandler handler = (ScreenHandler) (Object) this;

        if (actionType == SlotActionType.PICKUP || actionType == SlotActionType.PICKUP_ALL) {
            if (!isContainerSlot(slotIndex)) return false;
            ItemStack cursor = handler.getCursorStack();
            return !cursor.isEmpty() && cursor.getItem() == Items.DRAGON_EGG;
        }

        if (actionType == SlotActionType.QUICK_MOVE) {
            if (!isValidSlotIndex(slotIndex)) return false;
            Slot source = this.slots.get(slotIndex);
            ItemStack sourceStack = source.getStack();
            return source.inventory instanceof PlayerInventory
                    && !sourceStack.isEmpty()
                    && sourceStack.getItem() == Items.DRAGON_EGG
                    && hasContainerSlots();
        }

        if (actionType == SlotActionType.SWAP) {
            if (!isContainerSlot(slotIndex)) return false;
            if (button < 0 || button >= PlayerInventory.getHotbarSize()) return false;
            ItemStack hotbarStack = player.getInventory().getStack(button);
            return !hotbarStack.isEmpty() && hotbarStack.getItem() == Items.DRAGON_EGG;
        }

        return false;
    }

    private boolean isValidSlotIndex(int slotIndex) {
        return slotIndex >= 0 && slotIndex < this.slots.size();
    }

    private boolean isContainerSlot(int slotIndex) {
        if (!isValidSlotIndex(slotIndex)) return false;
        Slot slot = this.slots.get(slotIndex);
        return !(slot.inventory instanceof PlayerInventory);
    }

    private boolean hasContainerSlots() {
        for (Slot slot : this.slots) {
            if (!(slot.inventory instanceof PlayerInventory)) return true;
        }
        return false;
    }
}
