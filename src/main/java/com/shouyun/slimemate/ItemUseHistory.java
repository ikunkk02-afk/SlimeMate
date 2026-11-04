package com.shouyun.slimemate;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

record ItemUseHistory(String lastUser, int uses) {
    static final ItemUseHistory EMPTY = new ItemUseHistory("", 0);
    static final Codec<ItemUseHistory> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.string(0, 16).fieldOf("last_user").forGetter(ItemUseHistory::lastUser),
            Codec.intRange(0, Integer.MAX_VALUE).fieldOf("uses").forGetter(ItemUseHistory::uses)
    ).apply(instance, ItemUseHistory::new));
    static final StreamCodec<ByteBuf, ItemUseHistory> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(16), ItemUseHistory::lastUser,
            ByteBufCodecs.VAR_INT, ItemUseHistory::uses,
            ItemUseHistory::new);

    ItemUseHistory recordUse(String username) {
        return new ItemUseHistory(username, uses < Integer.MAX_VALUE ? uses + 1 : uses);
    }

    MutableComponent display() {
        Component user = lastUser.isEmpty() ? Component.translatable("tooltip.slimemate.no_user") : Component.literal(lastUser);
        return Component.translatable("tooltip.slimemate.use_history", user, uses);
    }
}
