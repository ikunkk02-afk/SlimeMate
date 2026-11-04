package com.shouyun.slimemate;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

@Mod(SlimeMate.MODID)
public class SlimeMate {
    public static final String MODID = "slimemate";

    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);
    private static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, MODID);
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, MODID);
    private static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, MODID);
    private static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(Registries.PARTICLE_TYPE, MODID);
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, MODID);
    private static final DeferredRegister<RecipeSerializer<?>> RECIPES = DeferredRegister.create(Registries.RECIPE_SERIALIZER, MODID);

    static final DeferredItem<SprayItem> CUP = ITEMS.registerItem("slime_cup", SprayItem::new, new Item.Properties().durability(64));
    static final DeferredItem<DollItem> DOLL = ITEMS.registerItem("slime_doll", DollItem::new, new Item.Properties().durability(64));
    static final DeferredItem<Item> USED_PAPER = ITEMS.registerSimpleItem("used_paper");
    static final DeferredHolder<EntityType<?>, EntityType<PlacedDoll>> PLACED_DOLL = ENTITIES.register("placed_doll", () ->
            EntityType.Builder.of(PlacedDoll::new, MobCategory.MISC).sized(0.77F, 0.90F)
                    .fireImmune().clientTrackingRange(6).build(MODID + ":placed_doll"));
    static final DeferredHolder<EntityType<?>, EntityType<SlimeShot>> SLIME_SHOT = ENTITIES.register("slime_shot", () ->
            EntityType.Builder.of(SlimeShot::new, MobCategory.MISC).sized(0.18F, 0.18F).noSave()
                    .clientTrackingRange(6).updateInterval(2).build(MODID + ":slime_shot"));
    static final DeferredHolder<EntityType<?>, EntityType<SlimePuddle>> SLIME_PUDDLE = ENTITIES.register("slime_puddle", () ->
            EntityType.Builder.of(SlimePuddle::new, MobCategory.MISC).sized(0.92F, 0.08F).noSave()
                    .clientTrackingRange(6).updateInterval(20).build(MODID + ":slime_puddle"));
    static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<DollUpgradeRecipe>> DOLL_UPGRADE =
            RECIPES.register("doll_upgrade", DollUpgradeRecipe.Serializer::new);
    static final DeferredHolder<DataComponentType<?>, DataComponentType<SlimeLiquid>> LIQUID =
            COMPONENTS.registerComponentType("liquid", builder -> builder.persistent(SlimeLiquid.CODEC));
    static final DeferredHolder<DataComponentType<?>, DataComponentType<ItemUseHistory>> USE_HISTORY =
            COMPONENTS.registerComponentType("use_history", builder -> builder
                    .persistent(ItemUseHistory.CODEC)
                    .networkSynchronized(ItemUseHistory.STREAM_CODEC));
    static final DeferredHolder<DataComponentType<?>, DataComponentType<ImportedSkin>> IMPORTED_SKIN =
            COMPONENTS.registerComponentType("imported_skin", builder -> builder
                    .persistent(ImportedSkin.CODEC)
                    .networkSynchronized(ImportedSkin.STREAM_CODEC));
    static final DeferredHolder<AttachmentType<?>, AttachmentType<DailyUse>> DAILY_USE =
            ATTACHMENTS.register("use_streak", () -> AttachmentType.builder(() -> DailyUse.EMPTY)
                    .serialize(DailyUse.CODEC, usage -> usage.count() > 0)
                    .copyOnDeath()
                    .sync((holder, player) -> holder == player, DailyUse.STREAM_CODEC)
                    .build());
    static final DeferredHolder<AttachmentType<?>, AttachmentType<Long>> SKIN_EDIT_TICK =
            ATTACHMENTS.register("skin_edit_tick", () -> AttachmentType.builder(() -> -20L).build());
    static final DeferredHolder<AttachmentType<?>, AttachmentType<Long>> SKIN_LOOKUP_TICK =
            ATTACHMENTS.register("skin_lookup_tick", () -> AttachmentType.builder(() -> -20L).build());
    static final DeferredHolder<MobEffect, FaintingEffect> FAINTED = EFFECTS.register("fainted", FaintingEffect::new);
    static final DeferredHolder<MobEffect, OveruseEffect> OVERUSED = EFFECTS.register("overused", OveruseEffect::new);
    static final DeferredHolder<ParticleType<?>, ParticleType<ColorParticleOption>> DROPLET =
            PARTICLES.register("droplet", DropletType::new);

    static {
        TABS.register("slimemate", () -> CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.slimemate"))
                .icon(() -> DOLL.get().getDefaultInstance())
                .displayItems((parameters, output) -> {
                    output.accept(CUP.get());
                    output.accept(DOLL.get());
                    output.accept(USED_PAPER.get());
                })
                .build());
    }

    public SlimeMate(IEventBus modEventBus, ModContainer container) {
        ITEMS.register(modEventBus);
        TABS.register(modEventBus);
        COMPONENTS.register(modEventBus);
        ATTACHMENTS.register(modEventBus);
        EFFECTS.register(modEventBus);
        PARTICLES.register(modEventBus);
        ENTITIES.register(modEventBus);
        RECIPES.register(modEventBus);
        modEventBus.addListener(SkinEditPayload::register);
        container.registerConfig(ModConfig.Type.SERVER, Config.SPEC);
        NeoForge.EVENT_BUS.addListener(OveruseEffect::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, SlimePuddle::onPaperWipe);
    }

    static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
