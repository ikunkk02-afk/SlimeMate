package com.shouyun.slimemate;

import java.util.Optional;
import java.util.function.Consumer;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.component.ResolvableProfile;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

record SkinLookupResult(int slot, String name, Optional<ResolvableProfile> profile, boolean retry) implements CustomPacketPayload {
    private static final Type<SkinLookupResult> TYPE = new Type<>(SlimeMate.id("skin_lookup_result"));
    private static final StreamCodec<ByteBuf, SkinLookupResult> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SkinLookupResult::slot,
            ByteBufCodecs.stringUtf8(16), SkinLookupResult::name,
            ResolvableProfile.STREAM_CODEC.apply(ByteBufCodecs::optional), SkinLookupResult::profile,
            ByteBufCodecs.BOOL, SkinLookupResult::retry,
            SkinLookupResult::new);
    private static Consumer<SkinLookupResult> clientHandler = result -> {};

    static void register(PayloadRegistrar registrar) {
        registrar.playToClient(TYPE, CODEC, (result, context) -> clientHandler.accept(result));
    }

    static void setClientHandler(Consumer<SkinLookupResult> handler) {
        clientHandler = handler;
    }

    @Override
    public Type<SkinLookupResult> type() {
        return TYPE;
    }
}
