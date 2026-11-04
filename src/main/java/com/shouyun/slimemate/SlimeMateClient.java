package com.shouyun.slimemate;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import org.lwjgl.glfw.GLFW;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ComputeFovModifierEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = SlimeMate.MODID, dist = Dist.CLIENT)
public class SlimeMateClient {
    private static final KeyMapping SKIN_KEY = new KeyMapping("key.slimemate.skin", GLFW.GLFW_KEY_K, "key.categories.slimemate");
    private static DollRenderer renderer;
    private static boolean waitingForUseRelease;

    public SlimeMateClient(IEventBus modBus, ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        SkinLookupResult.setClientHandler(result -> {
            if (Minecraft.getInstance().screen instanceof SkinScreen screen) {
                screen.acceptLookup(result);
            }
        });
        modBus.addListener((RegisterKeyMappingsEvent event) -> event.register(SKIN_KEY));
        modBus.addListener((RegisterParticleProvidersEvent event) -> event.registerSpriteSet(SlimeMate.DROPLET.get(), DropletParticle::provider));
        modBus.addListener(SlimeMateClient::registerItemRenderer);
        modBus.addListener((EntityRenderersEvent.RegisterRenderers event) -> {
            event.registerEntityRenderer(SlimeMate.PLACED_DOLL.get(), PlacedDollRenderer::new);
            event.registerEntityRenderer(SlimeMate.SLIME_SHOT.get(), NoopRenderer::new);
            event.registerEntityRenderer(SlimeMate.SLIME_PUDDLE.get(), SlimePuddleRenderer::new);
        });
        NeoForge.EVENT_BUS.addListener(SlimeMateClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(SlimeMateClient::onUseInput);
        NeoForge.EVENT_BUS.addListener(SlimeMateClient::onMouseReleased);
        NeoForge.EVENT_BUS.addListener(SlimeMateClient::onKeyReleased);
        NeoForge.EVENT_BUS.addListener(SlimeMateClient::zoomWhileUsing);
        NeoForge.EVENT_BUS.addListener(SlimeMateClient::shakeWhileUsing);
    }

    private static void onUseInput(InputEvent.InteractionKeyMappingTriggered event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (!event.isUseItem() || player == null) {
            return;
        }
        ItemStack stack = player.getItemInHand(event.getHand());
        if (stack.getItem() instanceof SprayItem) {
            if (waitingForUseRelease) {
                event.setSwingHand(false);
                event.setCanceled(true);
            } else if (stack.has(SlimeMate.LIQUID.get())) {
                waitingForUseRelease = true;
            }
        }
    }

    private static void onMouseReleased(InputEvent.MouseButton.Post event) {
        if (waitingForUseRelease && event.getAction() == GLFW.GLFW_RELEASE
                && Minecraft.getInstance().options.keyUse.matchesMouse(event.getButton())) {
            waitingForUseRelease = false;
        }
    }

    private static void onKeyReleased(InputEvent.Key event) {
        if (waitingForUseRelease && event.getAction() == GLFW.GLFW_RELEASE
                && Minecraft.getInstance().options.keyUse.matches(event.getKey(), event.getScanCode())) {
            waitingForUseRelease = false;
        }
    }

    private static void zoomWhileUsing(ComputeFovModifierEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = event.getPlayer();
        if (player != minecraft.player || !minecraft.options.getCameraType().isFirstPerson() || !isUsingSprayItem(player)) {
            return;
        }

        float progress = Mth.clamp(player.getTicksUsingItem() / 20.0F, 0.0F, 1.0F);
        float strength = minecraft.options.fovEffectScale().get().floatValue();
        event.setNewFovModifier(event.getNewFovModifier() * (1.0F - 0.06F * progress * progress * strength));
    }

    private static void shakeWhileUsing(ViewportEvent.ComputeCameraAngles event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || event.getCamera().getEntity() != player || event.getCamera().isDetached() || !isUsingSprayItem(player)) {
            return;
        }

        float elapsed = player.getTicksUsingItem() + (float) event.getPartialTick();
        float strength = Mth.clamp(elapsed / 6.0F, 0.0F, 1.0F) * minecraft.options.screenEffectScale().get().floatValue();
        event.setPitch(event.getPitch() + Mth.sin(elapsed * 0.90F) * 0.17F * strength);
        event.setRoll(event.getRoll() + Mth.sin(elapsed * 0.72F) * 0.14F * strength);
    }

    private static boolean isUsingSprayItem(Player player) {
        return player.isUsingItem() && player.getUseItemRemainingTicks() > 0 && player.getUseItem().getItem() instanceof SprayItem;
    }

    private static void registerItemRenderer(RegisterClientExtensionsEvent event) {
        event.registerItem(new SprayItemExtensions(), SlimeMate.CUP);
        event.registerItem(new SprayItemExtensions() {
            @Override
            public DollRenderer getCustomRenderer() {
                return dollRenderer();
            }
        }, SlimeMate.DOLL);
    }

    static DollRenderer dollRenderer() {
        if (renderer == null) {
            renderer = new DollRenderer(Minecraft.getInstance());
        }
        return renderer;
    }

    private static class SprayItemExtensions implements IClientItemExtensions {
        @Override
        public boolean applyForgeHandTransform(PoseStack pose, LocalPlayer player, HumanoidArm arm, ItemStack stack,
                                              float partialTick, float equipProgress, float swingProgress) {
            if (!player.isUsingItem() || player.getUseItemRemainingTicks() <= 0
                    || !ItemStack.isSameItemSameComponents(player.getUseItem(), stack)) {
                return false;
            }
            HumanoidArm activeArm = player.getUsedItemHand() == InteractionHand.MAIN_HAND
                    ? player.getMainArm() : player.getMainArm().getOpposite();
            if (arm != activeArm) {
                return false;
            }
            // Keep the standard resting grip while the item is being held down.
            int side = arm == HumanoidArm.RIGHT ? 1 : -1;
            pose.translate(side * 0.56F, -0.52F - equipProgress * 0.6F, -0.72F);
            pose.mulPose(Axis.YP.rotationDegrees(side * 45.0F));
            return true;
        }
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !minecraft.options.keyUse.isDown()) {
            waitingForUseRelease = false;
        } else if (isUsingSprayItem(minecraft.player)) {
            waitingForUseRelease = true;
        }
        while (SKIN_KEY.consumeClick()) {
            if (minecraft.screen != null || minecraft.player == null || minecraft.player.isSpectator()) {
                continue;
            }
            int slot = minecraft.player.getInventory().selected;
            ItemStack doll = minecraft.player.getMainHandItem();
            if (!doll.is(SlimeMate.DOLL.get())) {
                slot = Inventory.SLOT_OFFHAND;
                doll = minecraft.player.getOffhandItem();
            }
            if (doll.is(SlimeMate.DOLL.get())) {
                minecraft.setScreen(new SkinScreen(slot, doll));
            }
        }
    }
}
