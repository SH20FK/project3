package com.project3.dread;

import com.mojang.authlib.GameProfile;
import com.project3.Project3Mod;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.*;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shadow Merchant — appears randomly in Gloom Void.
 * Trades items for Dread points.
 * Speaks in riddles, disappears after 60 seconds or 16 blocks distance.
 */
public class ShadowMerchant {

    private static final Map<UUID, MerchantData> ACTIVE_MERCHANTS = new ConcurrentHashMap<>();
    private static final int MERCHANT_LIFETIME = 1200; // 60 seconds
    private static final int MERCHANT_RANGE = 16;

    public record MerchantItem(String name, ItemStack stack, int dreadCost, int maxBuys, int remaining) {}

    public static class MerchantData {
        public final ServerPlayerEntity npc;
        public final ServerPlayerEntity targetPlayer;
        public int ticksLeft;
        public final List<MerchantItem> inventory;
        public final Map<Integer, Integer> buyCounts = new HashMap<>();

        public MerchantData(ServerPlayerEntity npc, ServerPlayerEntity targetPlayer, List<MerchantItem> inventory) {
            this.npc = npc;
            this.targetPlayer = targetPlayer;
            this.ticksLeft = MERCHANT_LIFETIME;
            this.inventory = inventory;
        }
    }

    // ─── Spawn ──────────────────────────────────────────────────────────────

    public static void trySpawn(ServerPlayerEntity player) {
        if (ACTIVE_MERCHANTS.containsKey(player.getUuid())) return;

        ServerWorld world = (ServerWorld) player.getEntityWorld();

        // Spawn 8-12 blocks away
        double theta = player.getRandom().nextDouble() * 2 * Math.PI;
        double dist = 8.0 + player.getRandom().nextDouble() * 4.0;
        double px = player.getX() + Math.cos(theta) * dist;
        double pz = player.getZ() + Math.sin(theta) * dist;
        double py = player.getY();

        // Create NPC
        GameProfile merchantProfile = new GameProfile(UUID.randomUUID(), "???");
        ServerPlayerEntity npc = new ServerPlayerEntity(
                world.getServer(),
                world,
                merchantProfile,
                player.getClientOptions()
        );
        npc.setPosition(px, py, pz);

        // Face player
        double dx = player.getX() - px;
        double dz = player.getZ() - pz;
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
        npc.setYaw(yaw);
        npc.setHeadYaw(yaw);
        npc.setBodyYaw(yaw);

        // Equip with dark clothes: black leather everything
        npc.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.BLACK_DYE));
        npc.equipStack(EquipmentSlot.CHEST, new ItemStack(Items.ENDER_EYE));
        npc.equipStack(EquipmentSlot.LEGS, Items.AIR.getDefaultStack());
        npc.equipStack(EquipmentSlot.FEET, Items.AIR.getDefaultStack());
        npc.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.ENDER_PEARL));
        npc.equipStack(OFFHAND_PLACEHOLDER, Items.AIR.getDefaultStack());

        // Build trade inventory
        List<MerchantItem> tradeList = buildTradeInventory();

        // Render to player
        renderNpc(npc, world, new Vec3d(px, py, pz));

        MerchantData data = new MerchantData(npc, player, tradeList);
        ACTIVE_MERCHANTS.put(player.getUuid(), data);

        // Eerie sound
        world.playSound(null, px, py, pz, SoundEvents.ENTITY_WARDEN_AMBIENT, SoundCategory.HOSTILE, 1.5f, 0.3f);
    }

    private static final EquipmentSlot OFFHAND_PLACEHOLDER = EquipmentSlot.OFFHAND;

    // ─── Trade Inventory ────────────────────────────────────────────────────

    private static List<MerchantItem> buildTradeInventory() {
        List<MerchantItem> all = new ArrayList<>();
        all.add(new MerchantItem("Алмаз", new ItemStack(Items.DIAMOND), 20, 2, 2));
        all.add(new MerchantItem("Gapple", new ItemStack(Items.GOLDEN_APPLE), 15, 3, 3));
        all.add(new MerchantItem("Enchanted Apple", new ItemStack(Items.ENCHANTED_GOLDEN_APPLE), 50, 1, 1));
        all.add(new MerchantItem("Totem of Undying", new ItemStack(Items.TOTEM_OF_UNDYING), 50, 1, 1));
        all.add(new MerchantItem("Ender Pearl", new ItemStack(Items.ENDER_PEARL), 10, 5, 5));
        all.add(new MerchantItem("Name Tag", new ItemStack(Items.NAME_TAG), 8, 3, 3));
        all.add(new MerchantItem("Saddle", new ItemStack(Items.SADDLE), 12, 2, 2));
        all.add(new MerchantItem("Ench. Book", new ItemStack(Items.BOOK), 25, 2, 2));
        all.add(new MerchantItem("Resonance Shard", new ItemStack(Items.AMETHYST_SHARD), 5, 3, 3));
        all.add(new MerchantItem("Mystery Box", new ItemStack(Items.CHEST), 12, 99, 99));

        // Pick 6 random
        Collections.shuffle(all);
        return all.subList(0, Math.min(6, all.size()));
    }

    // ─── Handle Trade Request ───────────────────────────────────────────────

    public static boolean handleBuy(ServerPlayerEntity player, int slotIndex) {
        MerchantData data = ACTIVE_MERCHANTS.get(player.getUuid());
        if (data == null || data.ticksLeft <= 0) return false;

        if (slotIndex < 0 || slotIndex >= data.inventory.size()) return false;

        MerchantItem item = data.inventory.get(slotIndex);
        int buyCount = data.buyCounts.getOrDefault(slotIndex, 0);
        if (buyCount >= item.maxBuys()) {
            return false;
        }

        if (!DreadManager.spendDread(player, item.dreadCost())) {
            player.playSound(SoundEvents.ENTITY_VILLAGER_NO, 1.0f, 0.5f);
            return false;
        }

        // Give item
        ItemStack drop = item.stack().copy();
        drop.setCount(1);
        player.getInventory().insertStack(drop);

        data.buyCounts.put(slotIndex, buyCount + 1);

        player.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 0.6f);

        return true;
    }

    // ─── Tick ───────────────────────────────────────────────────────────────

    public static void tickAll(MinecraftServer server) {
        Iterator<Map.Entry<UUID, MerchantData>> it = ACTIVE_MERCHANTS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, MerchantData> entry = it.next();
            MerchantData data = entry.getValue();
            data.ticksLeft--;

            ServerPlayerEntity player = data.targetPlayer;
            ServerPlayerEntity npc = data.npc;

            if (data.ticksLeft <= 0 || !player.isAlive() || npc.isRemoved()) {
                destroyMerchant(data);
                it.remove();
                continue;
            }

            // Check distance
            double dist = player.getEntityPos().distanceTo(npc.getEntityPos());
            if (dist > MERCHANT_RANGE) {
                destroyMerchant(data);
                it.remove();
                continue;
            }

            // Face player every 20 ticks
            if (data.ticksLeft % 20 == 0) {
                npc.lookAt(net.minecraft.command.argument.EntityAnchorArgumentType.EntityAnchor.EYES, player.getEntityPos());
                broadcastPos(npc);
            }

            // Particles every 10 ticks
            if (data.ticksLeft % 10 == 0) {
                ServerWorld world = (ServerWorld) npc.getEntityWorld();
                Vec3d pos = npc.getEntityPos();
                world.spawnParticles(
                        DustParticleEffect.DEFAULT,
                        pos.x, pos.y + 1.0, pos.z,
                        2, 0.3, 0.3, 0.3, 0.01
                );
            }

        }
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private static void destroyMerchant(MerchantData data) {
        ServerPlayerEntity npc = data.npc;
        ServerWorld world = (ServerWorld) npc.getEntityWorld();
        Vec3d pos = npc.getEntityPos();

        // Particles on vanish
        world.spawnParticles(ParticleTypes.LARGE_SMOKE,
                pos.x, pos.y + 1.0, pos.z, 60, 0.5, 0.5, 0.5, 0.1);
        world.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.HOSTILE, 1.5f, 0.3f);

        // Remove packets
        for (ServerPlayerEntity viewer : world.getPlayers()) {
            if (viewer.getEntityPos().squaredDistanceTo(pos) < 64 * 64 && viewer.networkHandler != null) {
                viewer.networkHandler.sendPacket(new EntitiesDestroyS2CPacket(npc.getId()));
                viewer.networkHandler.sendPacket(new PlayerRemoveS2CPacket(List.of(npc.getUuid())));
            }
        }
    }

    private static void renderNpc(ServerPlayerEntity npc, ServerWorld world, Vec3d pos) {
        if (npc == null || npc.isRemoved() || !npc.isAlive()) return;
        for (ServerPlayerEntity viewer : world.getPlayers()) {
            if (viewer == null || viewer.networkHandler == null || viewer.isRemoved() || !viewer.isAlive()) continue;
            if (viewer.getEntityPos().squaredDistanceTo(pos) < 64 * 64) {
                viewer.networkHandler.sendPacket(new PlayerListS2CPacket(PlayerListS2CPacket.Action.ADD_PLAYER, npc));
                viewer.networkHandler.sendPacket(new EntitySpawnS2CPacket(
                        npc.getId(), npc.getUuid(),
                        npc.getX(), npc.getY(), npc.getZ(),
                        npc.getPitch(), npc.getYaw(),
                        net.minecraft.entity.EntityType.PLAYER,
                        0, Vec3d.ZERO, npc.getHeadYaw()
                ));
                List<com.mojang.datafixers.util.Pair<EquipmentSlot, ItemStack>> equipment = new ArrayList<>();
                for (EquipmentSlot slot : EquipmentSlot.values()) {
                    equipment.add(new com.mojang.datafixers.util.Pair<>(slot, npc.getEquippedStack(slot)));
                }
                viewer.networkHandler.sendPacket(new EntityEquipmentUpdateS2CPacket(npc.getId(), equipment));
                var dirtyEntries = npc.getDataTracker().getDirtyEntries();
                if (dirtyEntries != null) {
                    viewer.networkHandler.sendPacket(new EntityTrackerUpdateS2CPacket(npc.getId(), dirtyEntries));
                }
            }
        }
    }

    private static void broadcastPos(ServerPlayerEntity npc) {
        ServerWorld world = (ServerWorld) npc.getEntityWorld();
        Vec3d pos = npc.getEntityPos();
        for (ServerPlayerEntity viewer : world.getPlayers()) {
            if (viewer.getEntityPos().squaredDistanceTo(pos) < 64 * 64 && viewer.networkHandler != null) {
                viewer.networkHandler.sendPacket(EntityPositionSyncS2CPacket.create(npc));
                viewer.networkHandler.sendPacket(new EntitySetHeadYawS2CPacket(npc, (byte)(npc.getHeadYaw() * 256.0F / 360.0F)));
            }
        }
    }

    public static void onDisconnect(UUID uuid) {
        MerchantData data = ACTIVE_MERCHANTS.remove(uuid);
        if (data != null) {
            destroyMerchant(data);
        }
    }
}
