package com.shouyun.slimemate;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

enum SlimeLiquid implements StringRepresentable {
    THICK("thick", 0xFFF9FCFF, 4.0F, 60, 6, 14, 24),
    MEDIUM("medium", 0xDAF9FCFF, 3.0F, 36, 2, 12, 18),
    NORMAL("normal", 0xAAF9FCFF, 2.0F, 24, 1, 10, 12),
    DILUTE("dilute", 0x65F9FCFF, 1.0F, 12, 0, 8, 7),
    BLOODY("bloody", 0xF2DC313E, 3.5F, 12, 0, 12, 10);

    static final Codec<SlimeLiquid> CODEC = StringRepresentable.fromEnum(SlimeLiquid::values);
    private static final SlimeLiquid[] BY_ID = values();
    private final String name;
    final int color;
    final float damage;
    final int slowTicks;
    final int slowAmplifier;
    final int droplets;
    final int puddleTicks;

    SlimeLiquid(String name, int color, float damage, int slowTicks, int slowAmplifier, int droplets, int puddleSeconds) {
        this.name = name;
        this.color = color;
        this.damage = damage;
        this.slowTicks = slowTicks;
        this.slowAmplifier = slowAmplifier;
        this.droplets = droplets;
        this.puddleTicks = puddleSeconds * 20;
    }

    static SlimeLiquid byId(int id) {
        return id >= 0 && id < BY_ID.length ? BY_ID[id] : NORMAL;
    }

    void stickTo(LivingEntity target) {
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, slowTicks, slowAmplifier));
    }

    static SlimeLiquid produced(int count, boolean bleeding) {
        if (bleeding) {
            return BLOODY;
        }
        return switch (count) {
            case 1 -> THICK;
            case 2 -> MEDIUM;
            case 3 -> NORMAL;
            default -> DILUTE;
        };
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
