package com.project3.mixin;

import com.project3.Project3Mod;
import com.project3.state.Project3State;
import com.project3.world.CalibrationManager;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.TeleportTarget;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerEntity.class)
public class MixinServerPlayerEntity {

    /**
     * Thread-local guard preventing StackOverflowError.
     * When we call player.teleportTo(newTarget) inside the mixin,
     * the mixin would re-inject and create infinite recursion.
     * This flag skips re-entry for those internal redirect calls.
     */
    private static final ThreadLocal<Boolean> IN_P3_REDIRECT = ThreadLocal.withInitial(() -> false);

    @Inject(method = "tick", at = @At("HEAD"))
    private void onTick(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
        ServerWorld world = (ServerWorld) player.getEntityWorld();
        
        // Instant void death in the End + Unnamed Effect
        if (world.getRegistryKey() == World.END && player.getY() < -60.0) {
            if (player.isAlive()) {
                Project3State state = Project3State.getOrCreate(world.getServer());
                state.setUnnamedEffectActive(player.getUuid(), true);
                player.damage(world, world.getDamageSources().outOfWorld(), Float.MAX_VALUE);
            }
        }
    }

    @Inject(method = "teleportTo", at = @At("HEAD"), cancellable = true)
    private void onTeleportTo(TeleportTarget target, CallbackInfoReturnable<Entity> cir) {
        // Guard: if we are already inside a redirect call, skip to avoid infinite recursion
        if (IN_P3_REDIRECT.get()) return;

        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
        ServerWorld destWorld = target.world();
        ServerWorld currentWorld = (ServerWorld) player.getEntityWorld();

        if (currentWorld == null || destWorld == null) return;

        Project3State state = Project3State.getOrCreate(((ServerWorld) player.getEntityWorld()).getServer());

        // 3. Block access to Nether/End if they are locked/unstarted
        if (destWorld.getRegistryKey() == World.NETHER) {
            Project3Mod.Act act = Project3Mod.getAct(state);
            boolean locked = !state.isSeasonStarted() || CalibrationManager.calibrationTicksLeft > 0 || (act == Project3Mod.Act.I && !state.isNetherForceUnlocked());
            if (locked && !player.isCreative()) {
                player.sendMessage(Text.literal("§c[Система] Доступ в Незер заблокирован."), false);
                cir.setReturnValue(player);
                return;
            }
        }
        else if (destWorld.getRegistryKey() == World.END) {
            Project3Mod.Act act = Project3Mod.getAct(state);
            boolean locked = !state.isSeasonStarted() || CalibrationManager.calibrationTicksLeft > 0 || ((act == Project3Mod.Act.I || act == Project3Mod.Act.II || act == Project3Mod.Act.III) && !state.isEndForceUnlocked());
            if (locked && !player.isCreative()) {
                player.sendMessage(Text.literal("§c[Система] Доступ в Энд заблокирован."), false);
                cir.setReturnValue(player);
                return;
            }
        }

        // 1. Entering Gloom Void from Nether when going back to Overworld
        if (currentWorld.getRegistryKey() == World.NETHER && destWorld.getRegistryKey() == World.OVERWORLD) {
            double px = player.getX();
            double pz = player.getZ();
            if (Math.abs(px) > 2499.0 || Math.abs(pz) > 2499.0) {
                ServerWorld voidWorld = ((ServerWorld) player.getEntityWorld()).getServer().getWorld(Project3Mod.GLOOM_VOID_WORLD_KEY);
                if (voidWorld != null) {
                    state.setLastNetherPortalPos(player.getUuid(), player.getX(), player.getY(), player.getZ());

                    int hash = player.getUuid().hashCode() & 0x7FFFFFFF;
                    double vx = (hash % 1000) * 1000.0;
                    double vy = 64.0;
                    double vz = ((hash / 1000) % 1000) * 1000.0;

                    BlockPos pPos = new BlockPos((int)vx, (int)vy, (int)vz);

                    // Build 4x5 vertical nether portal frame aligned with X-axis
                    for (int dx = -1; dx <= 2; dx++) {
                        for (int dy = 0; dy <= 4; dy++) {
                            BlockPos framePos = pPos.add(dx, dy, 0);
                            if (dx == -1 || dx == 2 || dy == 0 || dy == 4) {
                                voidWorld.setBlockState(framePos, Blocks.OBSIDIAN.getDefaultState());
                            } else {
                                voidWorld.setBlockState(framePos, Blocks.NETHER_PORTAL.getDefaultState()
                                    .with(net.minecraft.block.NetherPortalBlock.AXIS, net.minecraft.util.math.Direction.Axis.X));
                            }
                        }
                    }

                    // Initialize the flickering portal ticks and state
                    com.project3.player.PlayerCooldowns.initPlayerVoidPortal(player.getUuid(), player.getRandom());

                    TeleportTarget newTarget = new TeleportTarget(
                        voidWorld,
                        new Vec3d(vx + 0.5, vy, vz + 1.5),
                        target.velocity(),
                        0.0f,
                        0.0f,
                        target.postTeleportTransition()
                    );

                    player.sendMessage(Text.literal("§cВы попытались выйти за рамки дозволенного... Пространство свернулось в темную пустоту."), false);

                    IN_P3_REDIRECT.set(true);
                    try {
                        cir.setReturnValue(player.teleportTo(newTarget));
                    } finally {
                        IN_P3_REDIRECT.set(false);
                    }
                }
            }
        }
        // 2. Exiting Gloom Void back to Nether
        else if (currentWorld.getRegistryKey() == Project3Mod.GLOOM_VOID_WORLD_KEY) {
            ServerWorld netherWorld = ((ServerWorld) player.getEntityWorld()).getServer().getWorld(World.NETHER);
            if (netherWorld != null) {
                String savedPos = state.getLastNetherPortalPos(player.getUuid());
                double sx, sy, sz;
                if (savedPos != null) {
                    try {
                        String[] parts = savedPos.split(",");
                        sx = Double.parseDouble(parts[0]);
                        sy = Double.parseDouble(parts[1]);
                        sz = Double.parseDouble(parts[2]);
                    } catch (Exception e) {
                        sx = 0.5; sy = 64; sz = 0.5;
                    }
                } else {
                    sx = 0.5; sy = 64; sz = 0.5;
                }
                // Find safe surface Y in the Nether at the saved X/Z
                int safeY = netherWorld.getTopPosition(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING,
                        new BlockPos((int)sx, 0, (int)sz)).getY();
                if (safeY <= netherWorld.getBottomY()) {
                    safeY = (int)sy; // fallback to saved Y
                }
                Vec3d targetPos = new Vec3d(sx, safeY + 1.0, sz);

                TeleportTarget newTarget = new TeleportTarget(
                    netherWorld,
                    targetPos,
                    target.velocity(),
                    target.yaw(),
                    target.pitch(),
                    target.postTeleportTransition()
                );

                player.sendMessage(Text.literal("§7[Система]: §eВы возвращены обратно в Незер."), false);

                IN_P3_REDIRECT.set(true);
                try {
                    cir.setReturnValue(player.teleportTo(newTarget));
                } finally {
                    IN_P3_REDIRECT.set(false);
                }
            }
        }
    }

    @Inject(method = "getPlayerListName", at = @At("RETURN"), cancellable = true)
    private void onGetPlayerListName(org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Text> cir) {
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
        ServerWorld world = (ServerWorld) player.getEntityWorld();
        Project3State state = Project3State.getOrCreate(world.getServer());
        
        int completed = state.getCompletedAchievements(player.getUuid()).size();
        
        net.minecraft.util.Formatting dimensionColor = net.minecraft.util.Formatting.WHITE;
        if (world.getRegistryKey() == World.NETHER) {
            dimensionColor = net.minecraft.util.Formatting.RED;
        } else if (world.getRegistryKey() == World.END || world.getRegistryKey() == com.project3.Project3Mod.GLOOM_VOID_WORLD_KEY) {
            dimensionColor = net.minecraft.util.Formatting.LIGHT_PURPLE;
        }
        
        Text customName = Text.literal("")
            .append(player.getName().copy().formatted(dimensionColor))
            .append(Text.literal(" [" + completed + "]").formatted(net.minecraft.util.Formatting.GRAY));
            
        cir.setReturnValue(customName);
    }
}
