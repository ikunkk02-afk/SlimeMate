package com.shouyun.slimemate;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;
import org.slf4j.Logger;

final class DollRenderer extends BlockEntityWithoutLevelRenderer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation DEFAULT_SKIN =
            ResourceLocation.withDefaultNamespace("textures/entity/player/wide/steve.png");
    private static final int TEXTURE_CACHE_SIZE = 128;
    private final Minecraft minecraft;
    private final PlayerModel<LivingEntity> classic;
    private final PlayerModel<LivingEntity> slim;
    private final Map<ResourceLocation, ResourceLocation> defaultTextures = new HashMap<>();
    private final Map<String, ResourceLocation> importedTextures = new LinkedHashMap<>(16, 0.75F, true);

    DollRenderer(Minecraft minecraft) {
        super(minecraft.getBlockEntityRenderDispatcher(), minecraft.getEntityModels());
        this.minecraft = minecraft;
        classic = seatedModel(false);
        slim = seatedModel(true);
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose,
                             MultiBufferSource buffers, int light, int overlay) {
        pose.pushPose();
        pose.translate(0.5F, 0.62F, 0.60F);
        renderModel(stack, pose, buffers, light, overlay);
        pose.popPose();
    }

    void renderModel(ItemStack stack, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        ImportedSkin imported = stack.get(SlimeMate.IMPORTED_SKIN.get());
        ResolvableProfile profile = stack.get(DataComponents.PROFILE);
        ResourceLocation texture;
        PlayerModel<LivingEntity> model = classic;
        if (imported != null) {
            texture = getImportedTexture(imported);
            model = imported.slim() ? slim : classic;
        } else if (profile != null && profile.id().isPresent()) {
            PlayerSkin skin = minecraft.getSkinManager().getInsecureSkin(profile.gameProfile());
            texture = skin.textureUrl() == null ? getDefaultTexture(skin.texture()) : skin.texture();
            model = skin.model() == PlayerSkin.Model.SLIM ? slim : classic;
        } else {
            texture = defaultTexture();
        }

        pose.mulPose(Axis.XP.rotationDegrees(180));
        VertexConsumer vertices = ItemRenderer.getFoilBufferDirect(
                buffers, RenderType.entityCutoutNoCull(texture), true, stack.hasFoil());
        model.renderToBuffer(pose, vertices, light, overlay);
    }

    ResourceLocation defaultTexture() {
        return getDefaultTexture(DEFAULT_SKIN);
    }

    private ResourceLocation getDefaultTexture(ResourceLocation source) {
        return defaultTextures.computeIfAbsent(source, this::loadDefaultTexture);
    }

    private ResourceLocation loadDefaultTexture(ResourceLocation source) {
        // Load the bundled skin directly so an enabled pack cannot replace it on dolls.
        try (InputStream stream = minecraft.getVanillaPackResources().asProvider().open(source)) {
            ResourceLocation texture = ResourceLocation.fromNamespaceAndPath(SlimeMate.MODID, "doll_skin/" + source.getPath());
            minecraft.getTextureManager().register(texture, new DynamicTexture(NativeImage.read(stream)));
            return texture;
        } catch (IOException exception) {
            LOGGER.warn("Could not load the built-in doll skin {}", source, exception);
            return source;
        }
    }

    private ResourceLocation getImportedTexture(ImportedSkin skin) {
        ResourceLocation existing = importedTextures.get(skin.pixels());
        if (existing != null) {
            return existing;
        }

        ByteBuffer pixels = ByteBuffer.wrap(skin.decodePixels());
        NativeImage image = new NativeImage(ImportedSkin.SIZE, ImportedSkin.SIZE, false);
        for (int y = 0; y < ImportedSkin.SIZE; y++) {
            for (int x = 0; x < ImportedSkin.SIZE; x++) {
                int rgba = pixels.getInt();
                image.setPixelRGBA(x, y, Integer.reverseBytes(rgba));
            }
        }
        ResourceLocation texture = minecraft.getTextureManager().register("slimemate_skin", new DynamicTexture(image));
        importedTextures.put(skin.pixels(), texture);
        if (importedTextures.size() > TEXTURE_CACHE_SIZE) {
            Iterator<ResourceLocation> iterator = importedTextures.values().iterator();
            minecraft.getTextureManager().release(iterator.next());
            iterator.remove();
        }
        return texture;
    }

    private static PlayerModel<LivingEntity> seatedModel(boolean slim) {
        // Bake a private copy without going through the shared player model layers.
        var mesh = LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, slim), 64, 64);
        PlayerModel<LivingEntity> model = new PlayerModel<>(mesh.bakeRoot(), slim);
        model.young = false;
        model.head.xRot = -0.06F;
        model.hat.copyFrom(model.head);
        model.leftArm.xRot = -0.40F;
        model.rightArm.xRot = -0.40F;
        model.leftArm.zRot = -0.16F;
        model.rightArm.zRot = 0.16F;
        model.leftLeg.xRot = -1.46F;
        model.rightLeg.xRot = -1.46F;
        model.leftLeg.yRot = -0.28F;
        model.rightLeg.yRot = 0.28F;
        model.leftSleeve.copyFrom(model.leftArm);
        model.rightSleeve.copyFrom(model.rightArm);
        model.leftPants.copyFrom(model.leftLeg);
        model.rightPants.copyFrom(model.rightLeg);
        return model;
    }
}
