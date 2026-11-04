package com.shouyun.slimemate;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

final class OveruseEffect extends MobEffect {
    OveruseEffect() {
        super(MobEffectCategory.HARMFUL, 0xA47D49);
        addAttributeModifier(Attributes.MOVEMENT_SPEED, SlimeMate.id("overused_speed"), -0.15,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        addAttributeModifier(Attributes.ATTACK_DAMAGE, SlimeMate.id("overused_damage"), -2,
                AttributeModifier.Operation.ADD_VALUE);
        addAttributeModifier(Attributes.BLOCK_BREAK_SPEED, SlimeMate.id("overused_mining"), -0.25,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }

    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && player.isAlive() && player.tickCount % 20 == 0) {
            DailyUse usage = player.getExistingData(SlimeMate.DAILY_USE).orElse(DailyUse.EMPTY);
            long dayTime = player.serverLevel().getServer().overworld().getDayTime();
            boolean overused = usage.countOn(dayTime) >= Config.OVERUSE_FROM.get();
            if (overused) {
                apply(player);
            } else if (player.hasEffect(SlimeMate.OVERUSED)) {
                player.removeEffect(SlimeMate.OVERUSED);
            }
        }
    }

    static void apply(ServerPlayer player) {
        if (!player.hasEffect(SlimeMate.OVERUSED)) {
            player.addEffect(new MobEffectInstance(SlimeMate.OVERUSED, MobEffectInstance.INFINITE_DURATION, 0, false, false, true));
        }
    }
}
