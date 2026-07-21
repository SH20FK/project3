package com.project3;

import com.project3.block.entity.ProducerBlockEntity;
import com.project3.block.entity.renderer.PhantomBlockEntityRenderer;
import com.project3.block.entity.renderer.ProducerBlockEntityRenderer;
import com.project3.client.ClientNetworkHandlers;
import com.project3.client.GloomVoidClientHandler;
import com.project3.client.hud.ActiveAchievementHud;
import com.project3.client.hud.DreadHandler;
import com.project3.client.hud.ParanoiaHandler;
import com.project3.client.particle.GloomMistParticle;
import com.project3.particle.ModParticles;
import com.project3.registry.ModRegistries;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/**
 * Client-side entry point for Project3.
 */
public class Project3Client implements ClientModInitializer {

    public static KeyBinding togglePanelKey;

    @Override
    public void onInitializeClient() {
        // ── Register Keybind ─────────────────────────────────────────────
        togglePanelKey = KeyBindingHelper.registerKeyBinding(
            new KeyBinding(
                "key.p3.toggle_panel",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_P,
                KeyBinding.Category.INVENTORY
            )
        );

        // ── Register Particles ────────────────────────────────────────────
        ParticleFactoryRegistry.getInstance().register(ModParticles.GLOOM_MIST, GloomMistParticle.Factory::new);

        // ── Register BlockEntityRenderers ─────────────────────────────────
        BlockEntityRendererRegistry.register(ModRegistries.PRODUCER_BLOCK_ENTITY_TYPE, ProducerBlockEntityRenderer::new);
        BlockEntityRendererRegistry.register(ModRegistries.PHANTOM_BLOCK_ENTITY_TYPE, PhantomBlockEntityRenderer::new);

        // ── Register Network ──────────────────────────────────────────────
        ClientNetworkHandlers.registerAll();

        // ── Register HUD Render ───────────────────────────────────────────
        HudRenderCallback.EVENT.register(ActiveAchievementHud::render);
        HudRenderCallback.EVENT.register((drawContext, renderTickCounter) -> {
            GloomVoidClientHandler.renderVignette(drawContext);
        });

        // ── Register Client Tick ──────────────────────────────────────────
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ActiveAchievementHud.tick();
            ParanoiaHandler.tick();
            DreadHandler.tick();
            GloomVoidClientHandler.tick(client);

            if (togglePanelKey != null) {
                while (togglePanelKey.wasPressed()) {
                    ActiveAchievementHud.panelVisible = !ActiveAchievementHud.panelVisible;
                }
            }
        });

        // ── Clean up on disconnect ────────────────────────────────────────
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ProducerBlockEntity.CLIENT_PRODUCERS.clear();
        });
    }
}
