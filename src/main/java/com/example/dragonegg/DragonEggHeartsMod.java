package com.example.dragonegg;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.item.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.Formatting;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class DragonEggHeartsMod implements ModInitializer {
    private static final String MOD_ID = "dragonegghearts";
    private static final Identifier MODIFIER_ID = Identifier.of(MOD_ID, "dragon_egg_health");
    private static final String RED_TEAM_NAME = "dragonegghearts_red";
    private static final int RESPAWN_SETTLE_TICKS = 20;

    private final Map<UUID, Boolean> hadEggLastTick = new HashMap<>();
    private final Map<UUID, Integer> respawnSettleTicks = new HashMap<>();

    @Override
    public void onInitialize() {
        DragonEggHeartsConfig.load();

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            Scoreboard scoreboard = server.getScoreboard();
            Set<UUID> online = new HashSet<>();
            server.getPlayerManager().getPlayerList().forEach(player -> {
                online.add(player.getUuid());

                Integer settle = respawnSettleTicks.get(player.getUuid());
                if (settle != null) {
                    if (settle > 0) {
                        respawnSettleTicks.put(player.getUuid(), settle - 1);
                        return;
                    }
                    respawnSettleTicks.remove(player.getUuid());
                }

                checkAndApply(player, scoreboard);
            });
            hadEggLastTick.keySet().removeIf(uuid -> !online.contains(uuid));
            respawnSettleTicks.keySet().removeIf(uuid -> !online.contains(uuid));
        });

        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            UUID uuid = newPlayer.getUuid();
            // Reset state tracking and briefly pause all mod mutations after respawn.
            hadEggLastTick.remove(uuid);
            respawnSettleTicks.put(uuid, RESPAWN_SETTLE_TICKS);
        });
    }

    private void checkAndApply(ServerPlayerEntity player, Scoreboard scoreboard) {
        if (!player.isAlive() || player.isRemoved()) return;

        boolean hasEgg = hasDragonEgg(player);
        DragonEggHeartsConfig.ConfigData config = DragonEggHeartsConfig.get();
        UUID uuid = player.getUuid();
        Boolean hadEgg = hadEggLastTick.get(uuid);
        EntityAttributeInstance inst = player.getAttributeInstance(EntityAttributes.MAX_HEALTH);
        if (inst == null) return;

        if (hasEgg) {
            if (inst.getModifier(MODIFIER_ID) == null) {
                EntityAttributeModifier mod = new EntityAttributeModifier(
                        MODIFIER_ID,
                        config.extraHealthAmount(),
                        EntityAttributeModifier.Operation.ADD_VALUE
                );
                inst.addTemporaryModifier(mod);
                if (player.getHealth() > inst.getValue()) player.setHealth((float) inst.getValue());
            }

            // Only touch team membership when state changes to reduce packet churn.
            if (config.redPlayerName && (hadEgg == null || !hadEgg)) {
                applyVisualStyle(player, scoreboard);
            }
            if (!config.redPlayerName) {
                removeVisualStyle(player, scoreboard);
            }
            player.setGlowing(config.outline);
        } else {
            if (inst.getModifier(MODIFIER_ID) != null) {
                inst.removeModifier(MODIFIER_ID);
                if (player.getHealth() > inst.getValue()) player.setHealth((float) inst.getValue());
            }

            if (hadEgg == null || hadEgg) {
                removeVisualStyle(player, scoreboard);
            }
            player.setGlowing(false);
        }

        hadEggLastTick.put(uuid, hasEgg);
    }

    private void applyVisualStyle(ServerPlayerEntity player, Scoreboard scoreboard) {
        Team redTeam = getOrCreateRedTeam(scoreboard);
        String scoreHolder = player.getNameForScoreboard();
        Team currentTeam = scoreboard.getScoreHolderTeam(scoreHolder);
        if (currentTeam != redTeam) {
            if (currentTeam != null) {
                scoreboard.removeScoreHolderFromTeam(scoreHolder, currentTeam);
            }
            scoreboard.addScoreHolderToTeam(scoreHolder, redTeam);
        }
    }

    private void removeVisualStyle(ServerPlayerEntity player, Scoreboard scoreboard) {
        String scoreHolder = player.getNameForScoreboard();
        Team currentTeam = scoreboard.getScoreHolderTeam(scoreHolder);
        Team redTeam = scoreboard.getTeam(RED_TEAM_NAME);
        if (currentTeam != null && currentTeam == redTeam) {
            scoreboard.removeScoreHolderFromTeam(scoreHolder, currentTeam);
        }
    }

    private Team getOrCreateRedTeam(Scoreboard scoreboard) {
        Team redTeam = scoreboard.getTeam(RED_TEAM_NAME);
        if (redTeam == null) {
            redTeam = scoreboard.addTeam(RED_TEAM_NAME);
        }
        redTeam.setColor(Formatting.RED);
        return redTeam;
    }

    private boolean hasDragonEgg(ServerPlayerEntity player) {
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.getItem() == Items.DRAGON_EGG) return true;
        }
        return false;
    }
}
