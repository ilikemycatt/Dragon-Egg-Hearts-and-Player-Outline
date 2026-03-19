package com.example.dragonegg;

import com.mojang.datafixers.util.Pair;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.mob.Angerable;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.AllayEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.EntityEquipmentUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.UpdateSelectedSlotS2CPacket;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class DragonEggHeartsMod implements ModInitializer {
    private static final String MOD_ID = "dragonegghearts";
    private static final Identifier MODIFIER_ID = Identifier.of(MOD_ID, "dragon_egg_health");
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
            if (!isDragonEgg(player.getMainHandStack()) && !isDragonEgg(player.getOffHandStack())) {
                return ActionResult.PASS;
            }

            DragonEggHeartsConfig.ConfigData config = DragonEggHeartsConfig.get();

            if (config.blockAllayEggInteractions && entity instanceof AllayEntity allay) {
                if (!world.isClient()
                        && world instanceof ServerWorld serverWorld
                        && player instanceof ServerPlayerEntity serverPlayer) {
                    stripDragonEggFromAllay(serverWorld, allay, "UseEntityCallback");
                    notifyAllayEggInteractionBlocked(serverPlayer, "UseEntityCallback");
                    syncBlockedAllayInteraction(serverPlayer, allay, "UseEntityCallback");
                }
                return ActionResult.FAIL;
            }

            if (config.blockEggUseOnItemFrames && entity instanceof ItemFrameEntity) {
                if (!world.isClient()) {
                    ServerWorld serverWorld = (ServerWorld) world;
                    int nowTick = serverWorld.getServer().getTicks();
                    Integer previousTick = lastEntityUseWarningTick.get(player.getUuid());
                    if (previousTick == null || nowTick != previousTick) {
                        player.sendMessage(Text.literal("Dragon Egg cannot be used with that entity."), false);
                        lastEntityUseWarningTick.put(player.getUuid(), nowTick);
                    }
                }
                return ActionResult.FAIL;
            }

            return ActionResult.PASS;
        });

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
                    respawnSetleDone(player.getUuid());
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
            UUID uuid = newPlayer.getUuid();
            hadEggLastTick.remove(uuid);
            eggCarrierCache.remove(uuid);
            respawnSettleTicks.put(uuid, RESPAWN_SETTLE_TICKS);
        });
    }

    private void checkAndApply(ServerPlayerEntity player, Scoreboard scoreboard) {
        if (!player.isAlive() || player.isRemoved()) return;

        boolean hasEgg = hasDragonEgg(player);
        DragonEggHeartsConfig.ConfigData config = DragonEggHeartsConfig.get();
        double desiredExtraHealth = config.extraHealthAmount();
        UUID uuid = player.getUuid();
        Boolean hadEgg = hadEggLastTick.get(uuid);
        EntityAttributeInstance inst = player.getAttributeInstance(EntityAttributes.MAX_HEALTH);
        if (inst == null) return;

        if (hasEgg) {
            eggCarrierCache.add(uuid);
            EntityAttributeModifier existingMod = inst.getModifier(MODIFIER_ID);
            if (existingMod == null || Double.compare(existingMod.value(), desiredExtraHealth) != 0) {
                if (existingMod != null) {
                    inst.removeModifier(MODIFIER_ID);
                }
                EntityAttributeModifier mod = new EntityAttributeModifier(
                        MODIFIER_ID,
                        desiredExtraHealth,
                        EntityAttributeModifier.Operation.ADD_VALUE
                );
                // Persistent modifier prevents relog from clamping players back to base
                // health before this mod reapplies the dragon egg bonus.
                inst.addPersistentModifier(mod);
                if (player.getHealth() > inst.getValue()) player.setHealth((float) inst.getValue());
            }

            if (config.redPlayerName && (hadEgg == null || !hadEgg)) {
                applyVisualStyle(player, scoreboard);
            }
            if (!config.redPlayerName) {
                removeVisualStyle(player, scoreboard);
            }
            if (hadEgg == null || !hadEgg) {
                player.setGlowing(config.outline);
            }

            if (player.age % 10 == 0) {
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
                player.setGlowing(false);
            }
        }

        hadEggLastTick.put(uuid, hasEgg);
    }

    private void angerNeutralMobs(ServerPlayerEntity player) {
        Box box = player.getBoundingBox().expand(NEUTRAL_AGGRO_RADIUS);
        ((ServerWorld) player.getEntityWorld()).getEntitiesByClass(MobEntity.class, box,
                mob -> mob instanceof Angerable && !(mob instanceof EndermanEntity)
        ).forEach(mob -> {
            if (mob.getTarget() == player) {
                return;
            }
            Angerable angerable = (Angerable) mob;
            angerable.chooseRandomAngerTime();
            mob.setTarget(player);
        });
    }

    private void prioritizeHostileMobs(ServerPlayerEntity player) {
        Box box = player.getBoundingBox().expand(HOSTILE_PRIORITY_RADIUS);
        ((ServerWorld) player.getEntityWorld()).getEntitiesByClass(HostileEntity.class, box,
                hostile -> !(hostile instanceof EndermanEntity)
        ).forEach(hostile -> {
            Entity currentTarget = hostile.getTarget();
            if (!(currentTarget instanceof ServerPlayerEntity currentPlayer)
                    || !isEggCarrierCached(currentPlayer)
                    || hostile.squaredDistanceTo(player) < hostile.squaredDistanceTo(currentPlayer)) {
                hostile.setTarget(player);
            }
        });
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

    private void processEggCoordinateAnnouncements(MinecraftServer server) {
        if (!DragonEggHeartsConfig.get().announceEggCoordinates) {
            clearAllEggAnnouncements();
            return;
        }

        if (pendingPlacementVerificationTick.isEmpty() && nextEggCoordsMessageTick.isEmpty()) {
            return;
        }

        int nowTick = server.getTicks();

        // First process pending placement verifications scheduled for this tick.
        Iterator<Map.Entry<EggAnnouncement, Integer>> verifyIterator = pendingPlacementVerificationTick.entrySet().iterator();
        Map<EggAnnouncement, Integer> verificationReplacements = null;
        while (verifyIterator.hasNext()) {
            Map.Entry<EggAnnouncement, Integer> entry = verifyIterator.next();
            if (nowTick < entry.getValue()) continue;

            EggAnnouncement announcement = entry.getKey();
            verifyIterator.remove();
            ServerWorld world = server.getWorld(announcement.world());
            if (world == null) {
                nextEggCoordsMessageTick.remove(announcement);
                missingEggRetryCount.remove(announcement);
                continue;
            }

            if (!world.getBlockState(announcement.pos()).isOf(Blocks.DRAGON_EGG)) {
                BlockPos movedPos = findNearbyDragonEgg(world, announcement.pos(), DRAGON_EGG_TELEPORT_SEARCH_RADIUS);
                if (movedPos != null) {
                    EggAnnouncement movedAnnouncement = new EggAnnouncement(announcement.world(), movedPos.toImmutable());
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
            ServerWorld world = server.getWorld(announcement.world());
            if (world == null) {
                iterator.remove();
                pendingPlacementVerificationTick.remove(announcement);
                missingEggRetryCount.remove(announcement);
                continue;
            }

            if (nowTick < entry.getValue()) {
                continue;
            }

            if (!world.getBlockState(announcement.pos()).isOf(Blocks.DRAGON_EGG)) {
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

                EggAnnouncement movedAnnouncement = new EggAnnouncement(announcement.world(), movedPos.toImmutable());
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

    public static boolean playerHasDragonEggInEitherHand(PlayerEntity player) {
        return isDragonEgg(player.getMainHandStack()) || isDragonEgg(player.getOffHandStack());
    }

    public static boolean stripDragonEggFromAllay(ServerWorld world, AllayEntity allay, String source) {
        boolean droppedEgg = false;
        for (Hand hand : Hand.values()) {
            ItemStack stackInHand = allay.getStackInHand(hand);
            if (!isDragonEgg(stackInHand)) {
                continue;
            }

            ItemStack dropped = stackInHand.copy();
            allay.setStackInHand(hand, ItemStack.EMPTY);
            allay.dropStack(world, dropped);
            droppedEgg = true;
        }

        for (int slot = 0; slot < allay.getInventory().size(); slot++) {
            ItemStack stack = allay.getInventory().getStack(slot);
            if (!isDragonEgg(stack)) {
                continue;
            }

            ItemStack dropped = stack.copy();
            allay.getInventory().setStack(slot, ItemStack.EMPTY);
            allay.dropStack(world, dropped);
            droppedEgg = true;
        }

        if (droppedEgg && DragonEggHeartsConfig.get().debugLogging) {
            System.out.println("[DragonEggHearts][AllayBlock] stripped egg from allay id="
                    + allay.getId() + " at " + allay.getBlockPos() + " source=" + source);
        }

        return droppedEgg;
    }

    public static void notifyAllayEggInteractionBlocked(ServerPlayerEntity player, String source) {
        MinecraftServer server = ((ServerWorld) player.getEntityWorld()).getServer();
        if (server == null) {
            return;
        }

        int nowTick = server.getTicks();
        Integer previousTick = lastAllayBlockWarningTick.get(player.getUuid());
        if (previousTick != null && nowTick - previousTick < ALLAY_WARNING_COOLDOWN_TICKS) {
            return;
        }

        player.sendMessage(Text.literal("Dragon Egg cannot be given to allays."), false);
        lastAllayBlockWarningTick.put(player.getUuid(), nowTick);

        if (DragonEggHeartsConfig.get().debugLogging) {
            System.out.println("[DragonEggHearts][AllayBlock] source=" + source
                    + " player=" + player.getName().getString()
                    + " tick=" + nowTick);
        }
    }

    public static void syncBlockedAllayInteraction(ServerPlayerEntity player, AllayEntity allay, String source) {
        // Force a corrective sync so client-side interaction prediction cannot
        // leave a ghost "allay is holding egg" state.
        player.playerScreenHandler.syncState();
        if (player.currentScreenHandler != player.playerScreenHandler) {
            player.currentScreenHandler.syncState();
        }

        int selectedSlot = player.getInventory().getSelectedSlot();
        player.networkHandler.sendPacket(new UpdateSelectedSlotS2CPacket(selectedSlot));
        player.networkHandler.sendPacket(player.getInventory().createSlotSetPacket(selectedSlot));

        EntityEquipmentUpdateS2CPacket packet = new EntityEquipmentUpdateS2CPacket(
                allay.getId(),
                List.of(
                        Pair.of(EquipmentSlot.MAINHAND, allay.getMainHandStack().copy()),
                        Pair.of(EquipmentSlot.OFFHAND, allay.getOffHandStack().copy())
                )
        );
        player.networkHandler.sendPacket(packet);

        if (DragonEggHeartsConfig.get().debugLogging) {
            System.out.println("[DragonEggHearts][AllayBlock] forced inventory/equipment sync source="
                    + source + " player=" + player.getName().getString());
        }
    }

    public static void notifyEggPlaced(ServerPlayerEntity player, BlockPos eggPos) {
        MinecraftServer server = ((ServerWorld) player.getEntityWorld()).getServer();
        if (server == null) {
            return;
        }

        EggAnnouncement announcement = new EggAnnouncement(
                player.getEntityWorld().getRegistryKey(),
                eggPos.toImmutable()
        );
        scheduleEggAnnouncement(server, announcement);
    }

    // Public helper to schedule an announcement when a dragon egg is placed
    // without a player context (e.g. teleport, other mods, or direct setBlockState).
    public static void notifyEggPlaced(ServerWorld world, BlockPos eggPos) {
        MinecraftServer server = world.getServer();
        if (server == null) return;

        EggAnnouncement announcement = new EggAnnouncement(world.getRegistryKey(), eggPos.toImmutable());
        scheduleEggAnnouncement(server, announcement);
    }

    public static void notifyEggTeleported(ServerWorld world, BlockPos oldPos, BlockPos newPos) {
        MinecraftServer server = world.getServer();
        if (server == null) {
            return;
        }

        if (!DragonEggHeartsConfig.get().announceEggCoordinates) {
            clearAllEggAnnouncements();
            return;
        }

        EggAnnouncement target = new EggAnnouncement(world.getRegistryKey(), newPos.toImmutable());
        int nowTick = server.getTicks();
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

        int nowTick = server.getTicks();
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

    public static void rebindEggAnnouncementAfterTeleport(ServerWorld world, BlockPos oldPos) {
        BlockPos newPos = findNearbyDragonEgg(world, oldPos, DRAGON_EGG_TELEPORT_SEARCH_RADIUS);
        if (newPos == null) {
            return;
        }

        notifyEggTeleported(world, oldPos, newPos);
    }

    private static void broadcastEggCoords(MinecraftServer server, EggAnnouncement announcement) {
        if (server.getWorld(announcement.world()) == null) {
            return;
        }

        Text message = Text.empty()
                .append(Text.literal("Dragon Egg").formatted(Formatting.LIGHT_PURPLE))
                .append(Text.literal(
                        " is in " + getDimensionName(announcement.world())
                                + " at X=" + announcement.pos().getX()
                                + " Y=" + announcement.pos().getY()
                                + " Z=" + announcement.pos().getZ()
                ));
        if (DragonEggHeartsConfig.get().debugLogging) {
            System.out.println("[DragonEggHearts] Broadcast egg coords for "
                    + announcement.pos() + " in world " + announcement.world());
        }
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            player.sendMessage(message, false);
        }
    }

    private static String getDimensionName(net.minecraft.registry.RegistryKey<World> worldKey) {
        if (worldKey.equals(World.OVERWORLD)) {
            return "Overworld";
        }
        if (worldKey.equals(World.NETHER)) {
            return "Nether";
        }
        if (worldKey.equals(World.END)) {
            return "End";
        }
        return worldKey.getValue().toString();
    }

    public static void restoreEggToEndPortal(MinecraftServer server) {
        if (!DragonEggHeartsConfig.get().restoreEggToEndPortalOnLoss) {
            return;
        }

        ServerWorld endWorld = server.getWorld(World.END);
        if (endWorld == null) {
            return;
        }

        BlockPos portalEggPos = findEndPortalEggPosition(endWorld);
        if (endWorld.getBlockState(portalEggPos).isOf(Blocks.DRAGON_EGG)) {
            return;
        }

        endWorld.breakBlock(portalEggPos, false);
        endWorld.setBlockState(portalEggPos, Blocks.DRAGON_EGG.getDefaultState(), 3);
    }

    private static BlockPos findEndPortalEggPosition(ServerWorld endWorld) {
        BlockPos origin = BlockPos.ORIGIN;

        int x = origin.getX();
        int z = origin.getZ();
        for (int y = endWorld.getTopYInclusive(); y >= endWorld.getBottomY(); y--) {
            BlockPos pos = new BlockPos(x, y, z);
            if (endWorld.getBlockState(pos).isOf(Blocks.BEDROCK)) {
                return pos.up();
            }
        }

        return new BlockPos(x, 64, z);
    }

    public static boolean hasDragonEgg(ServerPlayerEntity player) {
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
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

    public static boolean isEggCarrierCached(ServerPlayerEntity player) {
        return eggCarrierCache.contains(player.getUuid());
    }

    private static int getEggCoordsMessageIntervalTicks() {
        return DragonEggHeartsConfig.get().eggCoordsMessageIntervalTicks;
    }

    private static BlockPos findNearbyDragonEgg(ServerWorld world, BlockPos origin, int radius) {
        int minX = origin.getX() - radius;
        int maxX = origin.getX() + radius;
        int minY = Math.max(world.getBottomY(), origin.getY() - radius);
        int maxY = Math.min(world.getTopYInclusive(), origin.getY() + radius);
        int minZ = origin.getZ() - radius;
        int maxZ = origin.getZ() + radius;

        BlockPos.Mutable mutable = new BlockPos.Mutable();
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    mutable.set(x, y, z);
                    if (world.getBlockState(mutable).isOf(Blocks.DRAGON_EGG)) {
                        return mutable.toImmutable();
                    }
                }
            }
        }

        return null;
    }

    private record EggAnnouncement(net.minecraft.registry.RegistryKey<World> world, BlockPos pos) {
    }
}
