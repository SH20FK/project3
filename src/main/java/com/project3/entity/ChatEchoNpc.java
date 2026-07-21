package com.project3.entity;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.packet.s2c.play.EntityPositionSyncS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitySetHeadYawS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

public class ChatEchoNpc extends NpcBase {
    public int chatEchoMsgCount = 0;
    public int chatEchoNextMsg = 0;

    public static final String[] GLITCHED_PHRASES = {
        "§c▞▚█ СИСТЕМА ПЕРЕГРУЖЕНА █▚▞",
        "§4П▞О▚М█О▞Г█И▚Т█Е",
        "§7я █▞▚ виж█у ▚▞█ т█еб▞я...",
        "§cОНО СМОТРИТ НА МЕНЯ ▞▚▞▚",
        "§4Ф▞А▚Й█Л С█Т█Е█Р█Т",
        "§8[ДАННЫЕ ПОВРЕЖДЕНЫ] █▞▚█",
        "§cВЫХОДА НЕТ ВЫХОДА НЕТ ВЫХОДА НЕТ",
        "§4С█И█С█Т█Е█М█А  У█М█И█Р█А█Е█Т",
        "§cГДЕ МОЕ ТЕЛО?! ▞▚█",
        "§eН█А█З█А█Д  Н█Е  С█М█О█Т█Р█И"
    };

    private static final String[] ECHO_SYSTEM_MSGS = {
        "§7[Сервер] §fВыполняется резервное копирование данных игрока %s...",
        "§c[Ошибка] §fНе удалось прочитать файл %s.dat — повреждена контрольная сумма",
        "§e[Предупреждение] §fАномальная активность в памяти игрока %s",
        "§4[Критическая ошибка] §fОбнаружено 2 (два) активных экземпляра игрока %s",
        "§7[Система] §fСинхронизация данных: %s — 0x%X байт повреждено",
        "§4[СБОЙ] §fСущность игрока %s десинхронизирована. Рекомендуется перезагрузка",
        "§8[Лог] §f%s: множественный вход в систему — возможен дубликат",
        "§c[ОШИБКА: 0x7F4A] §fОбнаружена временная аномалия в потоке памяти игрока %s"
    };

    public ChatEchoNpc(ServerPlayerEntity npc, ServerPlayerEntity targetPlayer, int ticksLeft) {
        super(npc, targetPlayer, ticksLeft);
        this.speed = 999.0;
    }

    @Override
    public boolean tick(ServerWorld world, Vec3d npcPos) {
        ServerPlayerEntity target = targetPlayer;
        if (gracePeriod > 0) {
            gracePeriod--;
        }

        if (speed != 999.0) {
            float lifePercent = 1.0f - ((float) ticksLeft / 600.0f);
            float targetYaw = (float) Math.toDegrees(Math.atan2(
                    target.getZ() - npcPos.z,
                    target.getX() - npcPos.x)) - 90.0f;
            float backYaw = targetYaw + 180.0f;

            float currentYaw = backYaw + (targetYaw - backYaw) * Math.min(lifePercent * 1.5f, 1.0f);
            npc.setYaw(currentYaw);
            npc.setHeadYaw(currentYaw);
            npc.setBodyYaw(currentYaw);

            PhantomReplicator.broadcastToViewers(npc, new EntitySetHeadYawS2CPacket(npc, (byte) (npc.getHeadYaw() * 256.0F / 360.0F)));
            PhantomReplicator.broadcastToViewers(npc, EntityPositionSyncS2CPacket.create(npc));

            if (chatEchoMsgCount < 5 && ticksLeft < (600 - chatEchoNextMsg)) {
                chatEchoMsgCount++;
                chatEchoNextMsg = 160 + world.random.nextInt(80);

                if (world.random.nextBoolean()) {
                    String phrase = GLITCHED_PHRASES[world.random.nextInt(GLITCHED_PHRASES.length)];
                    target.sendMessage(Text.literal("<" + target.getGameProfile().name() + "> " + phrase), false);
                } else {
                    String sysMsg = ECHO_SYSTEM_MSGS[world.random.nextInt(ECHO_SYSTEM_MSGS.length)];
                    String formatted;
                    if (sysMsg.contains("%s")) {
                        long corruptBytes = (long)(world.random.nextDouble() * 0xFFFF);
                        formatted = String.format(sysMsg, target.getGameProfile().name(), corruptBytes);
                    } else {
                        formatted = String.format(sysMsg, target.getGameProfile().name());
                    }
                    target.sendMessage(Text.literal(formatted), false);
                }

                world.playSound(null, npcPos.x, npcPos.y, npcPos.z,
                        SoundEvents.ENTITY_ITEM_FRAME_BREAK, SoundCategory.HOSTILE, 0.6f, 1.5f);
            }

            boolean vanish = false;
            if (gracePeriod <= 0) {
                double distToPlayer = target.getEntityPos().squaredDistanceTo(npcPos);
                vanish = distToPlayer < 4.0 * 4.0;
                if (!vanish) {
                    Vec3d lookVec = target.getRotationVec(1.0f).normalize();
                    Vec3d toNpc = npcPos.subtract(target.getEntityPos()).normalize();
                    if (lookVec.dotProduct(toNpc) > 0.95) {
                        vanish = true;
                    }
                }
            }

            if (vanish) {
                speed = 999.0;
                ticksLeft = 12;

                world.playSound(null, npcPos.x, npcPos.y + 1.0, npcPos.z,
                        SoundEvents.ENTITY_SKELETON_HURT, SoundCategory.HOSTILE, 2.0f, 0.4f);
                world.playSound(null, npcPos.x, npcPos.y + 1.0, npcPos.z,
                        SoundEvents.ENTITY_ITEM_FRAME_BREAK, SoundCategory.HOSTILE, 1.5f, 0.5f);

                if (target.networkHandler != null) {
                    ServerPlayNetworking.send(target, new com.project3.network.PhantomHeadSnapPayload(npc.getId()));
                }
            }
        } else if (ticksLeft == 1) {
            PhantomReplicator.destroyNpc(this);
            world.playSound(null, npcPos.x, npcPos.y + 1.0, npcPos.z,
                    SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.HOSTILE, 1.5f, 0.2f);
            world.spawnParticles(ParticleTypes.LARGE_SMOKE,
                    npcPos.x, npcPos.y + 1.0, npcPos.z, 60, 0.5, 0.5, 0.5, 0.1);
            return true;
        }
        return false;
    }
}
