package com.shouyun.slimemate;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

final class DollItem extends SprayItem {
    DollItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        InteractionResult washed = super.useOn(context);
        Player player = context.getPlayer();
        if (washed.consumesAction() || player == null || !player.isShiftKeyDown()
                || context.getClickedFace() != Direction.UP || player.hasEffect(SlimeMate.FAINTED)) {
            return washed;
        }
        Level level = context.getLevel();
        BlockPos position = context.getClickedPos().above();
        ItemStack stack = context.getItemInHand();
        if (level.isOutsideBuildHeight(position) || !level.getWorldBorder().isWithinBounds(position)
                || !level.mayInteract(player, position) || !player.mayUseItemAt(position, Direction.UP, stack)) {
            return InteractionResult.FAIL;
        }
        if (!level.isClientSide) {
            PlacedDoll doll = new PlacedDoll(SlimeMate.PLACED_DOLL.get(), level);
            doll.setPos(position.getX() + 0.5, position.getY(), position.getZ() + 0.5);
            doll.setAppearance(stack, player);
            if (!doll.hasSupport() || !level.noCollision(doll) || !level.isUnobstructed(doll)
                    || !level.addFreshEntity(doll)) {
                return InteractionResult.FAIL;
            }
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.slimemate.place").withColor(0xAAAAAA));
        tooltip.add(Component.translatable("tooltip.slimemate.skin_key", Component.keybind("key.slimemate.skin")).withColor(0xAAAAAA));
        ResolvableProfile profile = stack.get(DataComponents.PROFILE);
        if (stack.has(SlimeMate.IMPORTED_SKIN.get())) {
            tooltip.add(Component.translatable("tooltip.slimemate.imported").withColor(0xA6E6AE));
        } else if (profile != null) {
            profile.name().ifPresent(name -> tooltip.add(Component.translatable("tooltip.slimemate.profile", name).withColor(0xA6E6AE)));
        }
    }
}
