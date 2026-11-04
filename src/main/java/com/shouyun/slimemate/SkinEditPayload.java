package com.shouyun.slimemate;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.ProfileLookupCallback;
import com.mojang.authlib.yggdrasil.ProfileResult;
import io.netty.buffer.ByteBuf;
import net.minecraft.Util;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringUtil;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

record SkinEditPayload(int slot, int action, String value) implements CustomPacketPayload {
    static final int RESET = 0;
    static final int PROFILE = 1;
    static final int CLASSIC_PNG = 2;
    static final int SLIM_PNG = 3;
    static final int ONLINE_PLAYER = 4;
    static final int LOOKUP_PROFILE = 5;

    private static final Type<SkinEditPayload> TYPE = new Type<>(SlimeMate.id("edit_skin"));
    private static final StreamCodec<ByteBuf, SkinEditPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SkinEditPayload::slot,
            ByteBufCodecs.VAR_INT, SkinEditPayload::action,
            ByteBufCodecs.stringUtf8(ImportedSkin.ENCODED_LENGTH), SkinEditPayload::value,
            SkinEditPayload::new
    );

    static void register(RegisterPayloadHandlersEvent event) {
        SkinLookupResult.register(event.registrar("6").playToServer(TYPE, CODEC, SkinEditPayload::handle));
    }

    @Override
    public Type<SkinEditPayload> type() {
        return TYPE;
    }

    private void handle(IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || player.isSpectator() || !player.isAlive()) {
            return;
        }
        ItemStack doll = heldStack(player);
        if (!doll.is(SlimeMate.DOLL.get())) {
            return;
        }

        MinecraftServer server = player.serverLevel().getServer();
        long now = server.overworld().getGameTime();
        if (action == LOOKUP_PROFILE) {
            lookupProfile(player, server, doll, now);
            return;
        }
        if (now - player.getData(SlimeMate.SKIN_EDIT_TICK) < 20) {
            player.displayClientMessage(Component.translatable("message.slimemate.skin_wait"), true);
            return;
        }
        player.setData(SlimeMate.SKIN_EDIT_TICK, now);

        switch (action) {
            case RESET -> {
                doll.remove(DataComponents.PROFILE);
                doll.remove(SlimeMate.IMPORTED_SKIN.get());
                saved(player);
            }
            case PROFILE -> {
                if (!value.isEmpty() && StringUtil.isValidPlayerName(value)) {
                    applyProfile(player, server, doll, now);
                }
            }
            case ONLINE_PLAYER -> applyOnlinePlayer(player, server, doll, now);
            case CLASSIC_PNG, SLIM_PNG -> {
                if (ImportedSkin.validate(value).isSuccess()) {
                    doll.set(SlimeMate.IMPORTED_SKIN.get(), new ImportedSkin(value, action == SLIM_PNG));
                    doll.remove(DataComponents.PROFILE);
                    saved(player);
                }
            }
            default -> {}
        }
    }

    private void lookupProfile(ServerPlayer player, MinecraftServer server, ItemStack doll, long now) {
        if (value.isEmpty() || value.length() > 16 || !StringUtil.isValidPlayerName(value)) {
            return;
        }
        if (now - player.getData(SlimeMate.SKIN_LOOKUP_TICK) < 20) {
            PacketDistributor.sendToPlayer(player, new SkinLookupResult(slot, value, Optional.empty(), true));
            return;
        }
        player.setData(SlimeMate.SKIN_LOOKUP_TICK, now);
        findProfile(server).whenCompleteAsync((profile, error) -> {
            if (server.getPlayerList().getPlayer(player.getUUID()) != player || heldStack(player) != doll
                    || player.getData(SlimeMate.SKIN_LOOKUP_TICK) != now) {
                return;
            }
            Optional<ResolvableProfile> result = error == null ? profile.map(ResolvableProfile::new) : Optional.empty();
            PacketDistributor.sendToPlayer(player, new SkinLookupResult(slot, value, result, false));
        }, server);
    }

    private CompletableFuture<Optional<GameProfile>> findProfile(MinecraftServer server) {
        return SkullBlockEntity.fetchGameProfile(value).thenCompose(profile -> {
            if (profile.isEmpty() || !profile.get().getId().equals(UUIDUtil.createOfflinePlayerUUID(profile.get().getName()))) {
                return CompletableFuture.completedFuture(profile);
            }
            // A cached offline identity is not proof that a Minecraft account exists.
            return CompletableFuture.supplyAsync(() -> {
                AtomicReference<GameProfile> result = new AtomicReference<>();
                server.getProfileRepository().findProfilesByNames(new String[] { value }, new ProfileLookupCallback() {
                    @Override
                    public void onProfileLookupSucceeded(GameProfile resolved) {
                        result.set(resolved);
                    }

                    @Override
                    public void onProfileLookupFailed(String name, Exception exception) {}
                });
                GameProfile resolved = result.get();
                if (resolved == null) {
                    return Optional.empty();
                }
                ProfileResult full = server.getSessionService().fetchProfile(resolved.getId(), true);
                return Optional.of(full == null ? resolved : full.profile());
            }, Util.backgroundExecutor());
        });
    }

    private ItemStack heldStack(ServerPlayer player) {
        if (slot == Inventory.SLOT_OFFHAND) {
            return player.getOffhandItem();
        }
        return slot == player.getInventory().selected ? player.getMainHandItem() : ItemStack.EMPTY;
    }

    private void applyOnlinePlayer(ServerPlayer player, MinecraftServer server, ItemStack doll, long requestTick) {
        if (value.length() != 36) {
            return;
        }
        UUID id;
        try {
            id = UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return;
        }
        ServerPlayer target = server.getPlayerList().getPlayer(id);
        if (target == null) {
            player.displayClientMessage(Component.translatable("message.slimemate.player_left"), true);
            return;
        }
        GameProfile profile = target.getGameProfile();
        if (profile.getProperties().containsKey("textures") || id.equals(UUIDUtil.createOfflinePlayerUUID(profile.getName()))) {
            setProfile(player, doll, profile);
            return;
        }
        SkullBlockEntity.fetchGameProfile(id).whenCompleteAsync((resolved, error) -> {
            if (stillEditing(player, server, doll, requestTick)) {
                setProfile(player, doll, error == null ? resolved.orElse(profile) : profile);
            }
        }, server);
    }

    private void applyProfile(ServerPlayer player, MinecraftServer server, ItemStack doll, long requestTick) {
        findProfile(server).whenCompleteAsync((profile, error) -> {
            if (!stillEditing(player, server, doll, requestTick)) {
                return;
            }
            if (error != null || profile.isEmpty()) {
                player.displayClientMessage(Component.translatable("message.slimemate.profile_missing", value), true);
                return;
            }
            setProfile(player, doll, profile.get());
        }, server);
    }

    private boolean stillEditing(ServerPlayer player, MinecraftServer server, ItemStack doll, long requestTick) {
        return server.getPlayerList().getPlayer(player.getUUID()) == player && player.isAlive() && !player.isSpectator()
                && heldStack(player) == doll && player.getData(SlimeMate.SKIN_EDIT_TICK) == requestTick;
    }

    private static void setProfile(ServerPlayer player, ItemStack doll, GameProfile profile) {
        GameProfile snapshot = new GameProfile(profile.getId(), profile.getName());
        snapshot.getProperties().putAll(profile.getProperties());
        doll.set(DataComponents.PROFILE, new ResolvableProfile(snapshot));
        doll.remove(SlimeMate.IMPORTED_SKIN.get());
        saved(player);
    }

    private static void saved(ServerPlayer player) {
        player.displayClientMessage(Component.translatable("message.slimemate.skin_saved"), true);
    }
}
