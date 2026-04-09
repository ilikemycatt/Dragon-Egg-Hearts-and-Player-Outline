package com.example.dragonegg;

import com.mojang.datafixers.util.Pair;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class DragonEggHeartsMod implements ModInitializer {
    private static final String MOD_ID = "dragonegghearts";
    private static final Identifier MODIFIER_ID = Identifier.fromNamespaceAndPath(MOD_ID, "dragon_egg_health");
    private static final String RED_TEAM_NAME = "dragonegghearts_red";
    private static final int RESPAWN_SETTLE_TICKS = 20;
    private static final int NEUTRAL_AGGRO_RADIUS = 24;
    private static final int HOSTILE_PRIORITY_RADIUS = 36;
    private static final int DRAGON_EGG_TELEPORT_SEARCH_RADIUS = 32;
    private static final int MISSING_EGG_RETRY_TICKS = 100;
    private static final int MISSING_EGG_MAX_RETRIES = 12;
    private static final int ALLAY_WARNING_COOLDOWN_TICKS = 10;

    private final Map<UUID, Boolean> hadEggLastTick = new HashMap<>();
    private final Map<UUID, Integer> respawnSettleTicks = new HashMap<>();
    private final Map<UUID, Integer> lastEntityUseWarningTick = new HashMap<>();
    private static final Set<UUID> eggCarrierCache = new HashSet<>();
    private static final Map<UUID, Integer> lastAllayBlockWarningTick = new HashMap<>();
    private static final Map<EggAnnouncement, Integer> nextEggCoordsMessageTick = new HashMap<>();
    // Short-lived verification map for recently scheduled placements. The value
    // is the server tick when verification should run (usually next tick).
    private static final Map<EggAnnouncement, Integer> pendingPlacementVerificationTick = new HashMap<>();
    private static final Map<EggAnnouncement, Integer> missingEggRetryCount = new HashMap<>();

    @Override
    public void onInitialize() {
        DragonEggHeartsConfig.load();

        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (!isDragonEgg(player.getMainHandItem()) && !isDragonEgg(player.getOffhandItem())) {
                return InteractionResult.PASS;
            }

            DragonEggHeartsConfig.ConfigData config = DragonEggHeartsConfig.get();

            if (config.blockAllayEggInteractions && entity instanceof Allay allay) {
                if (!world.isClientSide()
                        && world instanceof ServerLevel serverWorld
                        && player instanceof ServerPlayer serverPlayer) {
                    stripDragonEggFromAllay(serverWorld, allay, "UseEntityCallback");
                    notifyAllayEggInteractionBlocked(serverPlayer, "UseEntityCallback");
                    syncBlockedAllayInteraction(serverPlayer, allay, "UseEntityCallback");
                }
                return InteractionResult.FAIL;
            }

            if (config.blockEggUseOnItemFrames && entity instanceof ItemFrame) {
                if (!world.isClientSide()) {
                    ServerLevel serverWorld = (ServerLevel) world;
                    int nowTick = serverWorld.getServer().getTickCount();
                    Integer previousTick = lastEntityUseWarningTick.get(player.getUUID());
                    if (previousTick == null || nowTick != previousTick) {
                        player.sendSystemMessage(Component.literal("Dragon Egg cannot be used with that entity."));
                        lastEntityUseWarningTick.put(player.getUUID(), nowTick);
                    }
                }
                return InteractionResult.FAIL;
            }

            return InteractionResult.PASS;
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            Scoreboard scoreboard = server.getScoreboard();
            Set<UUID> online = new HashSet<>();

            server.getPlayerList().getPlayers().forEach(player -> {
                online.add(player.getUUID());

                Integer settle = respawnSettleTicks.get(player.getUUID());
                if (settle != null) {
                    if (settle > 0) {
                        respawnSettleTicks.put(player.getUUID(), settle - 1);
                        return;
                    }
                    respawnSetleDone(player.getUUID());
                }

                checkAndApply(player, scoreboard);
            });

            hadEggLastTick.keySet().removeIf(uuid -> !online.contains(uuid));
            respawnSettleTicks.keySet().removeIf(uuid -> !online.contains(uuid));
            lastEntityUseWarningTick.keySet().removeIf(uuid -> !online.contains(uuid));
            lastAllayBlockWarningTick.keySet().removeIf(uuid -> !online.contains(uuid));
            eggCarrierCache.removeIf(uuid -> !online.contains(uuid));

            processEggCoordinateAnnouncements(server);
        });

        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            UUID uuid = newPlayer.getUUID();
            hadEggLastTick.remove(uuid);
            eggCarrierCache.remove(uuid);
            respawnSettleTicks.put(uuid, RESPAWN_SETTLE_TICKS);
        });
    }

    private void checkAndApply(ServerPlayer player, Scoreboard scoreboard) {
        if (!player.isAlive() || player.isRemoved()) return;

        boolean hasEgg = hasDragonEgg(player);
        DragonEggHeartsConfig.ConfigData config = DragonEggHeartsConfig.get();
        double desiredExtraHealth = config.extraHealthAmount();
        UUID uuid = player.getUUID();
        Boolean hadEgg = hadEggLastTick.get(uuid);
        AttributeInstance inst = player.getAttribute(Attributes.MAX_HEALTH);
        if (inst == null) return;

        if (hasEgg) {
            eggCarrierCache.add(uuid);
            AttributeModifier existingMod = inst.getModifier(MODIFIER_ID);
            if (existingMod == null || Double.compare(existingMod.amount(), desiredExtraHealth) != 0) {
                if (existingMod != null) {
                    inst.removeModifier(MODIFIER_ID);
                }
                AttributeModifier mod = new AttributeModifier(
                        MODIFIER_ID,
                        desiredExtraHealth,
                        AttributeModifier.Operation.ADD_VALUE
                );
                // Persistent modifier prevents relog from clamping players back to base
                // health before this mod reapplies the dragon egg bonus.
                inst.addPermanentModifier(mod);
                if (player.getHealth() > inst.getValue()) player.setHealth((float) inst.getValue());
            }

            if (config.redPlayerName && (hadEgg == null || !hadEgg)) {
                applyVisualStyle(player, scoreboard);
            }
            if (!config.redPlayerName) {
                removeVisualStyle(player, scoreboard);
            }
            if (hadEgg == null || !hadEgg) {
                player.setGlowingTag(config.outline);
            }

            if (player.tickCount % 10 == 0) {
                if (config.angerNeutralMobsToEggCarriers) {
                    angerNeutralMobs(player);
                }
                if (config.prioritizeHostilesToEggCarriers) {
                    prioritizeHostileMobs(player);
                }
            }
        } else {
            eggCarrierCache.remove(uuid);
            if (inst.getModifier(MODIFIER_ID) != null) {
                inst.removeModifier(MODIFIER_ID);
                if (player.getHealth() > inst.getValue()) player.setHealth((float) inst.getValue());
            }

            if (hadEgg == null || hadEgg) {
                removeVisualStyle(player, scoreboard);
                player.setGlowingTag(false);
            }
        }

        hadEggLastTick.put(uuid, hasEgg);
    }

    private void angerNeutralMobs(ServerPlayer player) {
        AABB box = player.getBoundingBox().inflate(NEUTRAL_AGGRO_RADIUS);
        ((ServerLevel) player.level()).getEntitiesOfClass(Mob.class, box,
                mob -> mob instanceof NeutralMob && !(mob instanceof EnderMan)
        ).forEach(mob -> {
            if (mob.getTarget() == player) {
                return;
            }
            NeutralMob angerable = (NeutralMob) mob;
            angerable.startPersistentAngerTimer();
            mob.setTarget(player);
        });
    }

    private void prioritizeHostileMobs(ServerPlayer player) {
        AABB box = player.getBoundingBox().inflate(HOSTILE_PRIORITY_RADIUS);
        ((ServerLevel) player.level()).getEntitiesOfClass(Monster.class, box,
                hostile -> !(hostile instanceof EnderMan)
        ).forEach(hostile -> {
            Entity currentTarget = hostile.getTarget();
            if (!(currentTarget instanceof ServerPlayer currentPlayer)
                    || !isEggCarrierCached(currentPlayer)
                    || hostile.distanceToSqr(player) < hostile.distanceToSqr(currentPlayer)) {
                hostile.setTarget(player);
            }
        });
    }

    private void applyVisualStyle(ServerPlayer player, Scoreboard scoreboard) {
        PlayerTeam redTeam = getOrCreateRedTeam(scoreboard);
        String scoreHolder = player.getScoreboardName();
        PlayerTeam currentTeam = scoreboard.getPlayersTeam(scoreHolder);
        if (currentTeam != redTeam) {
            if (currentTeam != null) {
                scoreboard.removePlayerFromTeam(scoreHolder, currentTeam);
            }
            scoreboard.addPlayerToTeam(scoreHolder, redTeam);
        }
    }

    private void removeVisualStyle(ServerPlayer player, Scoreboard scoreboard) {
        String scoreHolder = player.getScoreboardName();
        PlayerTeam currentTeam = scoreboard.getPlayersTeam(scoreHolder);
        PlayerTeam redTeam = scoreboard.getPlayerTeam(RED_TEAM_NAME);
        if (currentTeam != null && currentTeam == redTeam) {
            scoreboard.removePlayerFromTeam(scoreHolder, currentTeam);
        }
    }

    private PlayerTeam getOrCreateRedTeam(Scoreboard scoreboard) {
        PlayerTeam redTeam = scoreboard.getPlayerTeam(RED_TEAM_NAME);
        if (redTeam == null) {
            redTeam = scoreboard.addPlayerTeam(RED_TEAM_NAME);
        }
        redTeam.setColor(ChatFormatting.RED);
        return redTeam;
    }

    private void processEggCoordinateAnnouncements(MinecraftServer server) {
        if (!DragonEggHeartsConfig.get().announceEggCoordinates) {
            clearAllEggAnnouncements();
            return;
        }

        if (pendingPlacementVerificationTick.isEmpty() && nextEggCoordsMessageTick.isEmpty()) {
            return;
        }

        int nowTick = server.getTickCount();

        // First process pending placement verifications scheduled for this tick.
        Iterator<Map.Entry<EggAnnouncement, Integer>> verifyIterator = pendingPlacementVerificationTick.entrySet().iterator();
        Map<EggAnnouncement, Integer> verificationReplacements = null;
        while (verifyIterator.hasNext()) {
            Map.Entry<EggAnnouncement, Integer> entry = verifyIterator.next();
            if (nowTick < entry.getValue()) continue;

            EggAnnouncement announcement = entry.getKey();
            verifyIterator.remove();
            ServerLevel world = server.getLevel(announcement.world());
            if (world == null) {
                nextEggCoordsMessageTick.remove(announcement);
                missingEggRetryCount.remove(announcement);
                continue;
            }

            if (!world.getBlockState(announcement.pos()).is(Blocks.DRAGON_EGG)) {
                BlockPos movedPos = findNearbyDragonEgg(world, announcement.pos(), DRAGON_EGG_TELEPORT_SEARCH_RADIUS);
                if (movedPos != null) {
                    EggAnnouncement movedAnnouncement = new EggAnnouncement(announcement.world(), movedPos.immutable());
                    if (verificationReplacements == null) {
                        verificationReplacements = new HashMap<>();
                    }
                    verificationReplacements.put(movedAnnouncement, nowTick + getEggCoordsMessageIntervalTicks());
                    missingEggRetryCount.remove(announcement);
                    missingEggRetryCount.remove(movedAnnouncement);
                    if (DragonEggHeartsConfig.get().debugLogging) {
                        System.out.println("[DragonEggHearts] Rebound egg announcement from "
                                + announcement.pos() + " to " + movedPos + " after verification");
                    }
                } else {
                    int retries = missingEggRetryCount.getOrDefault(announcement, 0) + 1;
                    if (retries > MISSING_EGG_MAX_RETRIES) {
                        nextEggCoordsMessageTick.remove(announcement);
                        missingEggRetryCount.remove(announcement);
                        if (DragonEggHeartsConfig.get().debugLogging) {
                            System.out.println("[DragonEggHearts] Stopped tracking missing egg at "
                                    + announcement.pos() + " in " + announcement.world());
                        }
                    } else {
                        // Keep tracking and retry shortly. Some modpacks can move
                        // blocks/entities asynchronously around teleports/falling.
                        missingEggRetryCount.put(announcement, retries);
                        nextEggCoordsMessageTick.put(announcement, nowTick + MISSING_EGG_RETRY_TICKS);
                        if (DragonEggHeartsConfig.get().debugLogging) {
                            System.out.println("[DragonEggHearts] Verification could not find egg near "
                                    + announcement.pos() + " in " + announcement.world()
                                    + "; retry " + retries + "/" + MISSING_EGG_MAX_RETRIES);
                        }
                    }
                }
            } else {
                missingEggRetryCount.remove(announcement);
            }
        }
        if (verificationReplacements != null) {
            nextEggCoordsMessageTick.putAll(verificationReplacements);
        }

        Iterator<Map.Entry<EggAnnouncement, Integer>> iterator = nextEggCoordsMessageTick.entrySet().iterator();
        Map<EggAnnouncement, Integer> replacements = null;

        while (iterator.hasNext()) {
            Map.Entry<EggAnnouncement, Integer> entry = iterator.next();
            EggAnnouncement announcement = entry.getKey();
            ServerLevel world = server.getLevel(announcement.world());
            if (world == null) {
                iterator.remove();
                pendingPlacementVerificationTick.remove(announcement);
                missingEggRetryCount.remove(announcement);
                continue;
            }

            if (nowTick < entry.getValue()) {
                continue;
            }

            if (!world.getBlockState(announcement.pos()).is(Blocks.DRAGON_EGG)) {
                BlockPos movedPos = findNearbyDragonEgg(world, announcement.pos(), DRAGON_EGG_TELEPORT_SEARCH_RADIUS);
                if (movedPos == null) {
                    int retries = missingEggRetryCount.getOrDefault(announcement, 0) + 1;
                    if (retries > MISSING_EGG_MAX_RETRIES) {
                        iterator.remove();
                        pendingPlacementVerificationTick.remove(announcement);
                        missingEggRetryCount.remove(announcement);
                        if (DragonEggHeartsConfig.get().debugLogging) {
                            System.out.println("[DragonEggHearts] Stopped tracking missing egg at "
                                    + announcement.pos() + " in " + announcement.world());
                        }
                    } else {
                        missingEggRetryCount.put(announcement, retries);
                        entry.setValue(nowTick + MISSING_EGG_RETRY_TICKS);
                        if (DragonEggHeartsConfig.get().debugLogging) {
                            System.out.println("[DragonEggHearts] Egg missing at " + announcement.pos()
                                    + " in " + announcement.world()
                                    + "; retry " + retries + "/" + MISSING_EGG_MAX_RETRIES);
                        }
                    }
                    continue;
                }

                EggAnnouncement movedAnnouncement = new EggAnnouncement(announcement.world(), movedPos.immutable());
                if (replacements == null) {
                    replacements = new HashMap<>();
                }
                replacements.put(movedAnnouncement, nowTick + getEggCoordsMessageIntervalTicks());
                missingEggRetryCount.remove(announcement);
                missingEggRetryCount.remove(movedAnnouncement);
                iterator.remove();
                continue;
            }

            broadcastEggCoords(server, announcement);
            missingEggRetryCount.remove(announcement);

            entry.setValue(nowTick + getEggCoordsMessageIntervalTicks());
        }

        if (replacements != null) {
            nextEggCoordsMessageTick.putAll(replacements);
        }
    }

    private void respawnSetleDone(UUID uuid) {
        respawnSettleTicks.remove(uuid);
    }

    public static boolean isDragonEgg(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() == Items.DRAGON_EGG;
    }

    public static boolean playerHasDragonEggInEitherHand(Player player) {
        return isDragonEgg(player.getMainHandItem()) || isDragonEgg(player.getOffhandItem());
    }

    public static boolean stripDragonEggFromAllay(ServerLevel world, Allay allay, String source) {
        boolean droppedEgg = false;
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stackInHand = allay.getItemInHand(hand);
            if (!isDragonEgg(stackInHand)) {
                continue;
            }

            ItemStack dropped = stackInHand.copy();
            allay.setItemInHand(hand, ItemStack.EMPTY);
            allay.spawnAtLocation(world, dropped);
            droppedEgg = true;
        }

        for (int slot = 0; slot < allay.getInventory().getContainerSize(); slot++) {
            ItemStack stack = allay.getInventory().getItem(slot);
            if (!isDragonEgg(stack)) {
                continue;
            }

            ItemStack dropped = stack.copy();
            allay.getInventory().setItem(slot, ItemStack.EMPTY);
            allay.spawnAtLocation(world, dropped);
            droppedEgg = true;
        }

        if (droppedEgg && DragonEggHeartsConfig.get().debugLogging) {
            System.out.println("[DragonEggHearts][AllayBlock] stripped egg from allay id="
                    + allay.getId() + " at " + allay.blockPosition() + " source=" + source);
        }

        return droppedEgg;
    }

    public static void notifyAllayEggInteractionBlocked(ServerPlayer player, String source) {
        MinecraftServer server = ((ServerLevel) player.level()).getServer();
        if (server == null) {
            return;
        }

        int nowTick = server.getTickCount();
        Integer previousTick = lastAllayBlockWarningTick.get(player.getUUID());
        if (previousTick != null && nowTick - previousTick < ALLAY_WARNING_COOLDOWN_TICKS) {
            return;
        }

        player.sendSystemMessage(Component.literal("Dragon Egg cannot be given to allays."));
        lastAllayBlockWarningTick.put(player.getUUID(), nowTick);

        if (DragonEggHeartsConfig.get().debugLogging) {
            System.out.println("[DragonEggHearts][AllayBlock] source=" + source
                    + " player=" + player.getName().getString()
                    + " tick=" + nowTick);
        }
    }

    public static void syncBlockedAllayInteraction(ServerPlayer player, Allay allay, String source) {
        // Force a corrective sync so client-side interaction prediction cannot
        // leave a ghost "allay is holding egg" state.
        player.inventoryMenu.sendAllDataToRemote();
        if (player.containerMenu != player.inventoryMenu) {
            player.containerMenu.sendAllDataToRemote();
        }

        int selectedSlot = player.getInventory().getSelectedSlot();
        player.connection.send(new ClientboundSetHeldSlotPacket(selectedSlot));
        player.connection.send(player.getInventory().createInventoryUpdatePacket(selectedSlot));

        ClientboundSetEquipmentPacket packet = new ClientboundSetEquipmentPacket(
                allay.getId(),
                List.of(
                        Pair.of(EquipmentSlot.MAINHAND, allay.getMainHandItem().copy()),
                        Pair.of(EquipmentSlot.OFFHAND, allay.getOffhandItem().copy())
                )
        );
        player.connection.send(packet);

        if (DragonEggHeartsConfig.get().debugLogging) {
            System.out.println("[DragonEggHearts][AllayBlock] forced inventory/equipment sync source="
                    + source + " player=" + player.getName().getString());
        }
    }

    public static void notifyEggPlaced(ServerPlayer player, BlockPos eggPos) {
        MinecraftServer server = ((ServerLevel) player.level()).getServer();
        if (server == null) {
            return;
        }

        EggAnnouncement announcement = new EggAnnouncement(
                player.level().dimension(),
                eggPos.immutable()
        );
        scheduleEggAnnouncement(server, announcement);
    }

    // Public helper to schedule an announcement when a dragon egg is placed
    // without a player context (e.g. teleport, other mods, or direct setBlockState).
    public static void notifyEggPlaced(ServerLevel world, BlockPos eggPos) {
        MinecraftServer server = world.getServer();
        if (server == null) return;

        EggAnnouncement announcement = new EggAnnouncement(world.dimension(), eggPos.immutable());
        scheduleEggAnnouncement(server, announcement);
    }

    public static void notifyEggTeleported(ServerLevel world, BlockPos oldPos, BlockPos newPos) {
        MinecraftServer server = world.getServer();
        if (server == null) {
            return;
        }

        if (!DragonEggHeartsConfig.get().announceEggCoordinates) {
            clearAllEggAnnouncements();
            return;
        }

        EggAnnouncement target = new EggAnnouncement(world.dimension(), newPos.immutable());
        int nowTick = server.getTickCount();
        clearAllEggAnnouncements();
        nextEggCoordsMessageTick.put(target, nowTick + getEggCoordsMessageIntervalTicks());
        pendingPlacementVerificationTick.put(target, nowTick + 1);
        missingEggRetryCount.remove(target);
        if (DragonEggHeartsConfig.get().debugLogging) {
            System.out.println("[DragonEggHearts] Tracked egg teleport from "
                    + oldPos + " to " + newPos + " in world " + target.world());
        }
    }

    private static void scheduleEggAnnouncement(MinecraftServer server, EggAnnouncement announcement) {
        if (!DragonEggHeartsConfig.get().announceEggCoordinates) {
            clearAllEggAnnouncements();
            return;
        }

        int nowTick = server.getTickCount();
        int scheduledTick = nowTick + getEggCoordsMessageIntervalTicks();
        Integer existing = nextEggCoordsMessageTick.get(announcement);
        if (existing != null && existing == scheduledTick) {
            return;
        }

        clearAllEggAnnouncements();
        nextEggCoordsMessageTick.put(announcement, scheduledTick);
        pendingPlacementVerificationTick.put(announcement, nowTick + 1);
        missingEggRetryCount.remove(announcement);

        if (DragonEggHeartsConfig.get().debugLogging) {
            System.out.println("[DragonEggHearts] Scheduled egg announcement for "
                    + announcement.pos() + " in world " + announcement.world());
        }
    }

    private static void clearAllEggAnnouncements() {
        nextEggCoordsMessageTick.clear();
        pendingPlacementVerificationTick.clear();
        missingEggRetryCount.clear();
    }

    public static void rebindEggAnnouncementAfterTeleport(ServerLevel world, BlockPos oldPos) {
        BlockPos newPos = findNearbyDragonEgg(world, oldPos, DRAGON_EGG_TELEPORT_SEARCH_RADIUS);
        if (newPos == null) {
            return;
        }

        notifyEggTeleported(world, oldPos, newPos);
    }

    private static void broadcastEggCoords(MinecraftServer server, EggAnnouncement announcement) {
        if (server.getLevel(announcement.world()) == null) {
            return;
        }

        Component message = Component.empty()
                .append(Component.literal("Dragon Egg").withStyle(ChatFormatting.LIGHT_PURPLE))
                .append(Component.literal(
                        " is in " + getDimensionName(announcement.world())
                                + " at X=" + announcement.pos().getX()
                                + " Y=" + announcement.pos().getY()
                                + " Z=" + announcement.pos().getZ()
                ));
        if (DragonEggHeartsConfig.get().debugLogging) {
            System.out.println("[DragonEggHearts] Broadcast egg coords for "
                    + announcement.pos() + " in world " + announcement.world());
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.sendSystemMessage(message);
        }
    }

    private static String getDimensionName(net.minecraft.resources.ResourceKey<Level> worldKey) {
        if (worldKey.equals(Level.OVERWORLD)) {
            return "Overworld";
        }
        if (worldKey.equals(Level.NETHER)) {
            return "Nether";
        }
        if (worldKey.equals(Level.END)) {
            return "End";
        }
        return worldKey.identifier().toString();
    }

    public static void restoreEggToEndPortal(MinecraftServer server) {
        if (!DragonEggHeartsConfig.get().restoreEggToEndPortalOnLoss) {
            return;
        }

        ServerLevel endWorld = server.getLevel(Level.END);
        if (endWorld == null) {
            return;
        }

        BlockPos portalEggPos = findEndPortalEggPosition(endWorld);
        if (endWorld.getBlockState(portalEggPos).is(Blocks.DRAGON_EGG)) {
            return;
        }

        endWorld.destroyBlock(portalEggPos, false);
        endWorld.setBlock(portalEggPos, Blocks.DRAGON_EGG.defaultBlockState(), 3);
    }

    private static BlockPos findEndPortalEggPosition(ServerLevel endWorld) {
        BlockPos origin = BlockPos.ZERO;

        int x = origin.getX();
        int z = origin.getZ();
        for (int y = endWorld.getMaxY(); y >= endWorld.getMinY(); y--) {
            BlockPos pos = new BlockPos(x, y, z);
            if (endWorld.getBlockState(pos).is(Blocks.BEDROCK)) {
                return pos.above();
            }
        }

        return new BlockPos(x, 64, z);
    }

    public static boolean hasDragonEgg(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (isDragonEgg(stack)) return true;
        }
        return false;
    }

    public static boolean areAllayEggInteractionsBlocked() {
        return DragonEggHeartsConfig.get().blockAllayEggInteractions;
    }

    public static boolean isEggRestorationEnabled() {
        return DragonEggHeartsConfig.get().restoreEggToEndPortalOnLoss;
    }

    public static boolean isEggCarrierCached(ServerPlayer player) {
        return eggCarrierCache.contains(player.getUUID());
    }

    private static int getEggCoordsMessageIntervalTicks() {
        return DragonEggHeartsConfig.get().eggCoordsMessageIntervalTicks;
    }

    private static BlockPos findNearbyDragonEgg(ServerLevel world, BlockPos origin, int radius) {
        int minX = origin.getX() - radius;
        int maxX = origin.getX() + radius;
        int minY = Math.max(world.getMinY(), origin.getY() - radius);
        int maxY = Math.min(world.getMaxY(), origin.getY() + radius);
        int minZ = origin.getZ() - radius;
        int maxZ = origin.getZ() + radius;

        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    mutable.set(x, y, z);
                    if (world.getBlockState(mutable).is(Blocks.DRAGON_EGG)) {
                        return mutable.immutable();
                    }
                }
            }
        }

        return null;
    }

    private record EggAnnouncement(net.minecraft.resources.ResourceKey<Level> world, BlockPos pos) {
    }
}
