package com.shouyun.slimemate;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

final class PlacedDollRenderer extends EntityRenderer<PlacedDoll> {
    PlacedDollRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.26F;
    }

    @Override
    public void render(PlacedDoll doll, float yaw, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int light) {
        ItemStack stack = doll.getDoll();
        if (!stack.isEmpty()) {
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(-doll.getFacingYaw()));
            pose.translate(0, 0.58F, -0.18F);
            pose.scale(0.60F, 0.60F, 0.60F);
            SlimeMateClient.dollRenderer().renderModel(stack, pose, buffers, light, OverlayTexture.NO_OVERLAY);
            pose.popPose();
            var viewer = Minecraft.getInstance().player;
            if (viewer != null && entityRenderDispatcher.distanceToSqr(doll) <= 64.0 && viewer.hasLineOfSight(doll)) {
                var history = stack.getOrDefault(SlimeMate.USE_HISTORY.get(), ItemUseHistory.EMPTY);
                renderNameTag(doll, history.display(), pose, buffers, light, partialTick);
            }
        }
        super.render(doll, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(PlacedDoll doll) {
        return SlimeMateClient.dollRenderer().defaultTexture();
    }
}
