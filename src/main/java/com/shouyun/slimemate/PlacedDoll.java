package com.shouyun.slimemate;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;

final class PlacedDoll extends Entity {
    private static final EntityDataAccessor<ItemStack> DOLL =
            SynchedEntityData.defineId(PlacedDoll.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<Float> FACING_YAW =
            SynchedEntityData.defineId(PlacedDoll.class, EntityDataSerializers.FLOAT);

    PlacedDoll(EntityType<? extends PlacedDoll> type, Level level) {
        super(type, level);
        noPhysics = true;
        blocksBuilding = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder data) {
        data.define(DOLL, ItemStack.EMPTY);
        data.define(FACING_YAW, 0.0F);
    }

    void setAppearance(ItemStack stack, Player player) {
        entityData.set(DOLL, stack.copyWithCount(1));
        facePlayer(player);
    }

    ItemStack getDoll() {
        return entityData.get(DOLL);
    }

    float getFacingYaw() {
        return entityData.get(FACING_YAW);
    }

    private void facePlayer(Player player) {
        double x = player.getX() - getX();
        double z = player.getZ() - getZ();
        float yaw = x * x + z * z < 1.0e-6
                ? player.getYRot() + 180.0F
                : (float) Math.toDegrees(Math.atan2(-x, z));
        entityData.set(FACING_YAW, Mth.wrapDegrees(yaw));
    }

    boolean hasSupport() {
        var support = blockPosition().below();
        return level().getBlockState(support).isFaceSturdy(level(), support, Direction.UP);
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide) {
            if (getDoll().isEmpty()) {
                discard();
            } else if (!hasSupport()) {
                dropDoll();
            }
        }
    }

    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    public ItemStack getPickResult() {
        return getDoll().copy();
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (!player.getItemInHand(hand).isEmpty() || player.isSpectator() || getDoll().isEmpty()) {
            return InteractionResult.PASS;
        }
        if (!level().isClientSide && !isRemoved()) {
            if (player.isShiftKeyDown()) {
                facePlayer(player);
            } else {
                player.setItemInHand(hand, getDoll().copy());
                discard();
            }
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide || isRemoved() || !(source.getEntity() instanceof Player) || isInvulnerableTo(source)) {
            return false;
        }
        dropDoll();
        return true;
    }

    private void dropDoll() {
        if (level().getGameRules().getBoolean(GameRules.RULE_DOENTITYDROPS)) {
            spawnAtLocation(getDoll().copy());
        }
        discard();
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(DOLL, ItemStack.parseOptional(registryAccess(), tag.getCompound("Doll")));
        float yaw = tag.contains("FacingYaw", Tag.TAG_ANY_NUMERIC)
                ? tag.getFloat("FacingYaw")
                : Direction.from2DDataValue(Mth.positiveModulo(tag.getInt("Facing"), 4)).toYRot();
        entityData.set(FACING_YAW, Float.isFinite(yaw) ? Mth.wrapDegrees(yaw) : 0.0F);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.put("Doll", getDoll().saveOptional(registryAccess()));
        tag.putFloat("FacingYaw", getFacingYaw());
    }
}
