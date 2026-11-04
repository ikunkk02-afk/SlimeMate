package com.shouyun.slimemate;

import java.util.Base64;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

record ImportedSkin(String pixels, boolean slim) {
    static final int SIZE = 64;
    static final int PIXEL_BYTES = SIZE * SIZE * 4;
    static final int ENCODED_LENGTH = (PIXEL_BYTES + 2) / 3 * 4;
    static final Codec<ImportedSkin> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.validate(ImportedSkin::validate).fieldOf("pixels").forGetter(ImportedSkin::pixels),
            Codec.BOOL.fieldOf("slim").forGetter(ImportedSkin::slim)
    ).apply(instance, ImportedSkin::new));
    static final StreamCodec<ByteBuf, ImportedSkin> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(ENCODED_LENGTH), ImportedSkin::pixels,
            ByteBufCodecs.BOOL, ImportedSkin::slim,
            ImportedSkin::new
    );

    static DataResult<String> validate(String pixels) {
        if (pixels.length() == ENCODED_LENGTH) {
            try {
                if (Base64.getDecoder().decode(pixels).length == PIXEL_BYTES) {
                    return DataResult.success(pixels);
                }
            } catch (IllegalArgumentException ignored) {
                // Invalid Base64 cannot represent a skin.
            }
        }
        return DataResult.error(() -> "Expected 64 by 64 RGBA skin pixels");
    }

    byte[] decodePixels() {
        return Base64.getDecoder().decode(pixels);
    }
}
