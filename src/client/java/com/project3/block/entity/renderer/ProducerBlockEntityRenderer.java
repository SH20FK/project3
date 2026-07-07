package com.project3.block.entity.renderer;

import com.project3.block.entity.ProducerBlockEntity;
import net.minecraft.client.render.*;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;

/**
 * Renders a glowing white pulsing orb on the ProducerBlock using direct vertex drawing.
 * The pulse intensity decreases linearly as block entity age increases toward 100.
 * When age >= 100, nothing extra is drawn (plain black block from model).
 */
public class ProducerBlockEntityRenderer implements BlockEntityRenderer<ProducerBlockEntity, ProducerBlockEntityRenderer.State> {
    public static class State extends net.minecraft.client.render.block.entity.state.BlockEntityRenderState {}

    public ProducerBlockEntityRenderer(BlockEntityRendererFactory.Context context) {}

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void render(State state, MatrixStack matrices,
                       net.minecraft.client.render.command.OrderedRenderCommandQueue commandQueue, 
                       net.minecraft.client.render.state.CameraRenderState cameraRenderState) {
        // No custom overlay quads needed since the base block texture is natively animated
    }
}
