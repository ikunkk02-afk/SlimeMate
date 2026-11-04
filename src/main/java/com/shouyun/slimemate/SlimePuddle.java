package com.shouyun.slimemate;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

final class SlimePuddle extends Entity {
    private static final float MAX_RADIUS = 0.46F;
    private static final double SURFACE_TOLERANCE = 0.001;
    private static final EntityDataAccessor<Integer> LIQUID =
            SynchedEntityData.defineId(SlimePuddle.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> RADIUS =
            SynchedEntityData.defineId(SlimePuddle.class, EntityDataSerializers.FLOAT);
    private BlockPos support;
    private UUID ownerId;

    SlimePuddle(EntityType<? extends SlimePuddle> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    static void onPaperWipe(PlayerInteractEvent.RightClickBlock event) {
        ItemStack paper = event.getItemStack();
        Player player = event.getEntity();
        if (!paper.is(Items.PAPER) || event.getFace() != Direction.UP || event.getUseItem().isFalse()
                || player.isSpectator() || player.isUsingItem()) {
            return;
        }

        Level level = event.getLevel();
        BlockPos block = event.getPos();
        if (!level.isClientSide && !level.mayInteract(player, block)) {
            return;
        }
        Vec3 point = event.getHitVec().getLocation();
        SlimePuddle puddle = level.getEntitiesOfClass(SlimePuddle.class, new AABB(point, point).inflate(0.13),
                candidate -> candidate.isAlive() && (level.isClientSide || block.equals(candidate.support))
                        && candidate.covers(point)).stream().findFirst().orElse(null);
        if (puddle == null) {
            return;
        }

        if (!level.isClientSide) {
            puddle.discard();
            ItemStack usedPaper = SlimeMate.USED_PAPER.get().getDefaultInstance();
            paper.shrink(1);
            if (paper.isEmpty()) {
                player.setItemInHand(event.getHand(), usedPaper);
            } else if (!player.getInventory().add(usedPaper)) {
                player.drop(usedPaper, false);
            }
            level.playSound(null, block, SoundEvents.SLIME_SQUISH_SMALL, player.getSoundSource(), 0.45F, 1.6F);
        }
        event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide));
        event.setCanceled(true);
    }

    private boolean covers(Vec3 point) {
        double x = point.x - getX();
        double z = point.z - getZ();
        return Math.abs(point.y - getY()) <= 0.125 && x * x + z * z <= radius() * radius();
    }

    static void place(ServerLevel level, BlockHitResult hit, SlimeLiquid liquid, Entity owner) {
        BlockPos block = hit.getBlockPos();
        Vec3 impact = hit.getLocation();
        if (!level.getWorldBorder().isWithinBounds(block) || owner instanceof Player player && !level.mayInteract(player, block)) {
            return;
        }

        for (AABB localShape : level.getBlockState(block).getCollisionShape(level, block).toAabbs()) {
            AABB surface = localShape.move(block);
            if (!onTopOf(surface, impact)) {
                continue;
            }
            float radius = (float) Math.min(MAX_RADIUS, Math.min(surface.getXsize(), surface.getZsize()) * 0.5);
            SlimePuddle puddle = new SlimePuddle(SlimeMate.SLIME_PUDDLE.get(), level);
            puddle.setPos(Mth.clamp(impact.x, surface.minX + radius, surface.maxX - radius), surface.maxY,
                    Mth.clamp(impact.z, surface.minZ + radius, surface.maxZ - radius));
            puddle.entityData.set(LIQUID, liquid.ordinal());
            puddle.entityData.set(RADIUS, radius);
            puddle.support = block.immutable();
            puddle.ownerId = owner == null ? null : owner.getUUID();

            if (level.addFreshEntity(puddle)) {
                for (SlimePuddle previous : level.getEntitiesOfClass(SlimePuddle.class, puddle.getBoundingBox())) {
                    if (previous != puddle && block.equals(previous.support)
                            && Math.abs(previous.getY() - puddle.getY()) < SURFACE_TOLERANCE) {
                        previous.discard();
                    }
                }
            }
            return;
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder data) {
        data.define(LIQUID, SlimeLiquid.NORMAL.ordinal());
        data.define(RADIUS, MAX_RADIUS);
    }

    SlimeLiquid liquid() {
        return SlimeLiquid.byId(entityData.get(LIQUID));
    }

    float radius() {
        return entityData.get(RADIUS);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        SlimeLiquid liquid = liquid();
        if (support == null || tickCount >= liquid.puddleTicks) {
            discard();
            return;
        }
        if (tickCount % 4 != 1) {
            return;
        }
        boolean supported = level.getBlockState(support).getCollisionShape(level, support).toAabbs().stream()
                .anyMatch(shape -> onTopOf(shape.move(support), position()));
        if (!supported) {
            discard();
            return;
        }

        double radius = radius() * 0.89;
        AABB area = new AABB(getX() - radius, getY() - 0.04, getZ() - radius,
                getX() + radius, getY() + 0.22, getZ() + radius);
        Player owner = ownerId == null ? null : level.getPlayerByUUID(ownerId);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area, LivingEntity::isAlive)) {
            if (target.isSpectator() || target.getY() < area.minY || target.getY() > area.maxY
                    || target instanceof Player other && (!(owner instanceof ServerPlayer player)
                        || player == other || !player.canHarmPlayer(other))) {
                continue;
            }
            AABB bounds = target.getBoundingBox();
            double dx = Mth.clamp(getX(), bounds.minX, bounds.maxX) - getX();
            double dz = Mth.clamp(getZ(), bounds.minZ, bounds.maxZ) - getZ();
            if (dx * dx + dz * dz <= radius * radius) {
                liquid.stickTo(target);
            }
        }
    }

    private static boolean onTopOf(AABB surface, Vec3 point) {
        return Math.abs(surface.maxY - point.y) < SURFACE_TOLERANCE
                && point.x >= surface.minX - SURFACE_TOLERANCE && point.x <= surface.maxX + SURFACE_TOLERANCE
                && point.z >= surface.minZ - SURFACE_TOLERANCE && point.z <= surface.maxZ + SURFACE_TOLERANCE;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}
}
