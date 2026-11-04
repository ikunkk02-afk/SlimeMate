package com.shouyun.slimemate;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;

final class DollUpgradeRecipe extends ShapedRecipe {
    private final ShapedRecipePattern pattern;
    private final ItemStack result;

    private DollUpgradeRecipe(String group, CraftingBookCategory category, ShapedRecipePattern pattern, ItemStack result, boolean notification) {
        super(group, category, pattern, result, notification);
        this.pattern = pattern;
        this.result = result;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        ItemStack doll = super.assemble(input, registries);
        for (ItemStack cup : input.items()) {
            if (cup.is(SlimeMate.CUP.get())) {
                doll.set(SlimeMate.LIQUID.get(), cup.get(SlimeMate.LIQUID.get()));
                doll.set(SlimeMate.USE_HISTORY.get(), cup.get(SlimeMate.USE_HISTORY.get()));
                doll.setDamageValue(Math.min(cup.getDamageValue(), Math.max(0, doll.getMaxDamage() - 1)));
                break;
            }
        }
        return doll;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return SlimeMate.DOLL_UPGRADE.get();
    }

    static final class Serializer implements RecipeSerializer<DollUpgradeRecipe> {
        private static final MapCodec<DollUpgradeRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.STRING.optionalFieldOf("group", "").forGetter(DollUpgradeRecipe::getGroup),
                CraftingBookCategory.CODEC.fieldOf("category").orElse(CraftingBookCategory.MISC).forGetter(DollUpgradeRecipe::category),
                ShapedRecipePattern.MAP_CODEC.forGetter(recipe -> recipe.pattern),
                ItemStack.STRICT_CODEC.fieldOf("result").forGetter(recipe -> recipe.result),
                Codec.BOOL.optionalFieldOf("show_notification", true).forGetter(DollUpgradeRecipe::showNotification)
        ).apply(instance, DollUpgradeRecipe::new));
        private static final StreamCodec<RegistryFriendlyByteBuf, DollUpgradeRecipe> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, DollUpgradeRecipe::getGroup,
                ByteBufCodecs.fromCodec(CraftingBookCategory.CODEC), DollUpgradeRecipe::category,
                ShapedRecipePattern.STREAM_CODEC, recipe -> recipe.pattern,
                ItemStack.STREAM_CODEC, recipe -> recipe.result,
                ByteBufCodecs.BOOL, DollUpgradeRecipe::showNotification,
                DollUpgradeRecipe::new);

        @Override
        public MapCodec<DollUpgradeRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, DollUpgradeRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
