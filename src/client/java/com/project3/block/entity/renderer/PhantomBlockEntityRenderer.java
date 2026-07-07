package com.project3.block.entity.renderer;

import com.project3.block.entity.PhantomBlockEntity;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.render.model.BlockStateModel;
import net.minecraft.client.util.math.MatrixStack;

public class PhantomBlockEntityRenderer implements BlockEntityRenderer<PhantomBlockEntity, PhantomBlockEntityRenderer.State> {
    public static class State extends net.minecraft.client.render.block.entity.state.BlockEntityRenderState {
        public BlockState replacedState = Blocks.STONE.getDefaultState();
    }

    public PhantomBlockEntityRenderer(BlockEntityRendererFactory.Context ctx) {}

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void updateRenderState(PhantomBlockEntity entity, State state, float tickDelta, net.minecraft.util.math.Vec3d vec, net.minecraft.client.render.command.ModelCommandRenderer.CrumblingOverlayCommand crumbling) {
        BlockEntityRenderer.super.updateRenderState(entity, state, tickDelta, vec, crumbling);
        BlockState bs = entity.getReplacedState();
        if (bs != null && !bs.isAir()) {
            state.replacedState = bs;
        } else {
            state.replacedState = Blocks.STONE.getDefaultState();
        }
    }

    @Override
    public void render(State state, MatrixStack matrices,
                       net.minecraft.client.render.command.OrderedRenderCommandQueue commandQueue,
                       net.minecraft.client.render.state.CameraRenderState cameraRenderState) {
        BlockState replacedState = state.replacedState;
        if (replacedState == null || replacedState.isAir()) return;

        MinecraftClient client = MinecraftClient.getInstance();
        net.minecraft.client.render.block.BlockRenderManager renderManager = client.getBlockRenderManager();
        BlockStateModel model = renderManager.getModel(replacedState);
        if (model == null) return;

        VertexConsumer consumer = client.getBufferBuilders().getEntityVertexConsumers().getBuffer(
                net.minecraft.client.render.RenderLayers.solid());

        net.minecraft.client.render.block.BlockModelRenderer.render(
                matrices.peek(),
                consumer,
                model,
                1.0f, 1.0f, 1.0f,
                net.minecraft.client.render.LightmapTextureManager.MAX_LIGHT_COORDINATE,
                net.minecraft.client.render.OverlayTexture.DEFAULT_UV
        );
    }
}
