package com.shouyun.slimemate;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

final class SlimePuddleRenderer extends EntityRenderer<SlimePuddle> {
    private static final ResourceLocation TEXTURE = SlimeMate.id("textures/entity/slime_puddle.png");

    SlimePuddleRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(SlimePuddle puddle, float yaw, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int light) {
        VertexConsumer vertices = buffers.getBuffer(RenderType.entityTranslucent(TEXTURE));
        float radius = puddle.radius();
        int color = puddle.liquid().color;
        quadVertex(vertices, pose.last(), -radius, -radius, 0, 0, color, light);
        quadVertex(vertices, pose.last(), -radius, radius, 0, 1, color, light);
        quadVertex(vertices, pose.last(), radius, radius, 1, 1, color, light);
        quadVertex(vertices, pose.last(), radius, -radius, 1, 0, color, light);
    }

    private static void quadVertex(VertexConsumer vertices, PoseStack.Pose pose, float x, float z,
                                   float u, float v, int color, int light) {
        vertices.addVertex(pose, x, 0.009F, z).setColor(color).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 1, 0);
    }

    @Override
    public ResourceLocation getTextureLocation(SlimePuddle puddle) {
        return TEXTURE;
    }
}
