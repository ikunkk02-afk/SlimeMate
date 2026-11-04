package com.shouyun.slimemate;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;

final class FaintingEffect extends MobEffect {
    FaintingEffect() {
        super(MobEffectCategory.HARMFUL, 0x36334F);
        addAttributeModifier(Attributes.MOVEMENT_SPEED, SlimeMate.id("fainted_speed"), -1,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        addAttributeModifier(Attributes.JUMP_STRENGTH, SlimeMate.id("fainted_jump"), -1,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return duration == 1;
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity instanceof ServerPlayer player && player.level().dimension().equals(Level.OVERWORLD)) {
            ServerLevel level = player.serverLevel();
            Config.TimeSkip policy = Config.TIME_SKIP.get();
            boolean singlePlayer = !level.getServer().isDedicatedServer() && !level.getServer().isPublished();
            if (level.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT)
                    && (policy == Config.TimeSkip.ALWAYS || policy == Config.TimeSkip.SINGLE_PLAYER && singlePlayer)) {
                boolean halfDay = player.getRandom().nextBoolean();
                level.setDayTime(level.getDayTime() + (halfDay ? DailyUse.DAY_TICKS / 2 : DailyUse.DAY_TICKS));
                player.displayClientMessage(Component.translatable(
                        halfDay ? "message.slimemate.skipped_half_day" : "message.slimemate.skipped_day"), true);
            }
        }
        return true;
    }
}
