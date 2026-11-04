package com.shouyun.slimemate;

import java.util.List;

import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.stats.Stats;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;

class SprayItem extends Item {
    private static final int USE_TICKS = 5 * 20;
    private static final int RANDOM_EFFECT_TICKS = 30 * 20;
    private static final int FAINT_TICKS = 60;
    private static final int BLEED_FROM = 10;
    private static final int FAINT_FROM = 11;
    private static final List<Holder<MobEffect>> POSITIVE_EFFECTS = List.of(
            MobEffects.MOVEMENT_SPEED, MobEffects.DIG_SPEED, MobEffects.REGENERATION, MobEffects.DAMAGE_RESISTANCE);
    private static final List<Holder<MobEffect>> NEGATIVE_EFFECTS = List.of(
            MobEffects.WEAKNESS, MobEffects.MOVEMENT_SLOWDOWN, MobEffects.DIG_SLOWDOWN, MobEffects.HUNGER);

    SprayItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isUsingItem() || player.hasEffect(SlimeMate.FAINTED) || player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(stack);
        }
        SlimeLiquid liquid = stack.get(SlimeMate.LIQUID.get());
        if (liquid != null) {
            if (player.isShiftKeyDown()) {
                if (wash(level, player, stack)) {
                    return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
                }
                if (!level.isClientSide) {
                    player.displayClientMessage(Component.translatable("message.slimemate.need_water"), true);
                }
                return InteractionResultHolder.fail(stack);
            }
            if (player instanceof ServerPlayer serverPlayer) {
                if (!SlimeShot.fire(serverPlayer, liquid)) {
                    return InteractionResultHolder.fail(stack);
                }
                stack.remove(SlimeMate.LIQUID.get());
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        int remaining = remainingCooldown(player);
        if (remaining > 0) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable("message.slimemate.cooldown", (remaining + 19L) / 20), true);
            }
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player != null && player.isShiftKeyDown() && !player.hasEffect(SlimeMate.FAINTED)
                && wash(context.getLevel(), player, context.getItemInHand())) {
            return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
        }
        return InteractionResult.PASS;
    }

    private static boolean wash(Level level, Player player, ItemStack stack) {
        if (!stack.has(SlimeMate.LIQUID.get())) {
            return false;
        }
        var hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.ANY);
        if (!player.isInWater() && (hit.getType() == HitResult.Type.MISS || !level.getFluidState(hit.getBlockPos()).is(FluidTags.WATER))) {
            return false;
        }
        if (!level.isClientSide) {
            stack.remove(SlimeMate.LIQUID.get());
            level.playSound(null, player.blockPosition(), SoundEvents.BOTTLE_FILL, player.getSoundSource(), 0.65F, 1.1F);
            player.displayClientMessage(Component.translatable("message.slimemate.washed"), true);
        }
        return true;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return USE_TICKS;
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int remaining) {
        if (entity instanceof ServerPlayer player && remaining > 0) {
            player.displayClientMessage(Component.translatable("message.slimemate.interrupted"), true);
        }
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!(entity instanceof ServerPlayer player) || !player.isAlive() || player.isSpectator()
                || player.hasEffect(SlimeMate.FAINTED) || stack.has(SlimeMate.LIQUID.get()) || remainingCooldown(player) > 0) {
            return stack;
        }

        ServerLevel overworld = player.serverLevel().getServer().overworld();
        long tick = overworld.getGameTime();
        long dayTime = overworld.getDayTime();
        DailyUse previous = player.getData(SlimeMate.DAILY_USE);
        int count = (int) Math.min((long) previous.countOn(dayTime) + 1, Integer.MAX_VALUE);
        int cooldownTicks = Config.COOLDOWN_SECONDS.get() * 20;
        player.setData(SlimeMate.DAILY_USE, new DailyUse(count, Math.floorDiv(dayTime, DailyUse.DAY_TICKS), tick + cooldownTicks));
        player.awardStat(Stats.ITEM_USED.get(this));
        if (stack.is(SlimeMate.CUP.get())) {
            CriteriaTriggers.CONSUME_ITEM.trigger(player, stack);
        }
        stack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(player.getUsedItemHand()));

        RandomSource random = player.getRandom();
        boolean bleeding = count >= BLEED_FROM && random.nextDouble() < Config.BLEED_CHANCE.get();
        boolean fainted = count >= FAINT_FROM && random.nextDouble() < Config.FAINT_CHANCE.get();
        SlimeLiquid liquid = SlimeLiquid.produced(count, bleeding);
        if (!stack.isEmpty()) {
            stack.set(SlimeMate.LIQUID.get(), liquid);
            stack.update(SlimeMate.USE_HISTORY.get(), ItemUseHistory.EMPTY,
                    history -> history.recordUse(player.getGameProfile().getName()));
            player.level().playSound(null, player.blockPosition(), SoundEvents.BOTTLE_FILL, player.getSoundSource(), 0.5F, 0.85F);
        }
        int overuseFrom = Config.OVERUSE_FROM.get();
        if (count >= overuseFrom) {
            OveruseEffect.apply(player);
            Holder<MobEffect> effect = NEGATIVE_EFFECTS.get(random.nextInt(NEGATIVE_EFFECTS.size()));
            player.addEffect(new MobEffectInstance(effect, RANDOM_EFFECT_TICKS));
        } else if (count <= Config.POSITIVE_USES.get()) {
            Holder<MobEffect> effect = POSITIVE_EFFECTS.get(random.nextInt(POSITIVE_EFFECTS.size()));
            player.addEffect(new MobEffectInstance(effect, RANDOM_EFFECT_TICKS));
        }

        MutableComponent feedback = Component.translatable("message.slimemate.daily_count", count);
        if (bleeding) {
            player.hurt(player.damageSources().generic(), 2.0F);
            feedback.append(" · ").append(Component.translatable("message.slimemate.bleeding"));
        }
        if (fainted && player.isAlive()) {
            player.addEffect(new MobEffectInstance(SlimeMate.FAINTED, FAINT_TICKS, 0, false, false, false));
            player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, FAINT_TICKS, 0, false, false, true));
            feedback.append(" · ").append(Component.translatable("message.slimemate.fainted"));
        }
        player.displayClientMessage(feedback, true);
        return stack;
    }

    private static int remainingCooldown(Player player) {
        DailyUse usage = player.getExistingData(SlimeMate.DAILY_USE).orElse(DailyUse.EMPTY);
        return usage.cooldownRemaining(player.level().getGameTime());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(stack.getOrDefault(SlimeMate.USE_HISTORY.get(), ItemUseHistory.EMPTY).display().withColor(0xD9D4B7));
        SlimeLiquid liquid = stack.get(SlimeMate.LIQUID.get());
        if (liquid != null) {
            Component name = Component.translatable("liquid.slimemate." + liquid.getSerializedName());
            tooltip.add(Component.translatable("tooltip.slimemate.loaded", name).withColor(0xC4EAE3));
            tooltip.add(Component.translatable("tooltip.slimemate.spray").withColor(0xAAAAAA));
            tooltip.add(Component.translatable("tooltip.slimemate.wash").withColor(0xAAAAAA));
            return;
        }
        tooltip.add(Component.translatable("tooltip.slimemate.use").withColor(0xAAAAAA));
        int cooldown = Config.SPEC.isLoaded() ? Config.COOLDOWN_SECONDS.get() : Config.DEFAULT_COOLDOWN_SECONDS;
        if (cooldown > 0) {
            tooltip.add(Component.translatable("tooltip.slimemate.cooldown", cooldown).withColor(0xAAAAAA));
        }
    }
}
