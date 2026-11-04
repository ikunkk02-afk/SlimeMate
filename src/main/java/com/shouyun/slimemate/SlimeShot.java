package com.shouyun.slimemate;

import net.minecraft.core.Direction;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

final class SlimeShot extends ThrowableProjectile {
    private static final EntityDataAccessor<Integer> LIQUID =
            SynchedEntityData.defineId(SlimeShot.class, EntityDataSerializers.INT);

    SlimeShot(EntityType<? extends SlimeShot> type, Level level) {
        super(type, level);
    }

    static boolean fire(ServerPlayer player, SlimeLiquid liquid) {
        ServerLevel level = player.serverLevel();
        SlimeShot shot = new SlimeShot(SlimeMate.SLIME_SHOT.get(), level);
        shot.setOwner(player);
        shot.setPos(player.getEyePosition().add(0, -0.12, 0));
        shot.entityData.set(LIQUID, liquid.ordinal());
        shot.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 0.72F, 0.0F);
        if (!level.addFreshEntity(shot)) {
            return false;
        }
        level.playSound(null, player.blockPosition(), SoundEvents.SLIME_SQUISH_SMALL, player.getSoundSource(), 0.45F, 1.25F);
        return true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder data) {
        data.define(LIQUID, SlimeLiquid.NORMAL.ordinal());
    }

    @Override
    protected double getDefaultGravity() {
        return 0.045;
    }

    @Override
    public boolean canUsePortal(boolean allowPassengers) {
        return false;
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        return entity instanceof LivingEntity && super.canHitEntity(entity);
    }

    @Override
    public void tick() {
        Vec3 previous = position();
        super.tick();
        if (isRemoved()) {
            return;
        }
        if (level().isClientSide) {
            SlimeLiquid liquid = SlimeLiquid.byId(entityData.get(LIQUID));
            ColorParticleOption color = ColorParticleOption.create(SlimeMate.DROPLET.get(), liquid.color);
            for (int i = 1; i <= 3; i++) {
                Vec3 point = previous.lerp(position(), i / 3.0);
                Vec3 velocity = getDeltaMovement().scale(0.18);
                level().addParticle(color, point.x, point.y, point.z, velocity.x, velocity.y, velocity.z);
            }
        } else if (tickCount >= 40 || isInWater()) {
            discard();
        }
    }

    @Override
    protected void onHit(HitResult hit) {
        super.onHit(hit);
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        SlimeLiquid liquid = SlimeLiquid.byId(entityData.get(LIQUID));
        if (hit instanceof BlockHitResult block && block.getDirection() == Direction.UP) {
            SlimePuddle.place(level, block, liquid, getOwner());
        }

        Vec3 point = hit.getLocation();
        ColorParticleOption droplets = ColorParticleOption.create(SlimeMate.DROPLET.get(), liquid.color);
        ColorParticleOption blood = ColorParticleOption.create(SlimeMate.DROPLET.get(), 0xFAFF646C);
        RandomSource random = getRandom();
        for (int i = 0; i < liquid.droplets; i++) {
            level.sendParticles(liquid == SlimeLiquid.BLOODY && i % 3 == 0 ? blood : droplets,
                    point.x, point.y + 0.03, point.z, 0,
                    random.nextGaussian() * 0.12, 0.05 + random.nextDouble() * 0.15, random.nextGaussian() * 0.12, 1);
        }
        discard();
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        if (!level().isClientSide && getOwner() instanceof ServerPlayer player
                && hit.getEntity() instanceof LivingEntity target
                && (!(target instanceof Player other) || player.canHarmPlayer(other))) {
            SlimeLiquid liquid = SlimeLiquid.byId(entityData.get(LIQUID));
            if (target.hurt(damageSources().thrown(this, player), liquid.damage)) {
                liquid.stickTo(target);
            }
        }
    }
}
