package com.shouyun.slimemate;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

record DailyUse(int count, long day, long cooldownUntil) {
    static final long DAY_TICKS = 24000L;
    static final DailyUse EMPTY = new DailyUse(0, 0, 0);
    private static final Codec<DailyUse> DAILY_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.intRange(0, Integer.MAX_VALUE).fieldOf("count").forGetter(DailyUse::count),
            Codec.LONG.fieldOf("day").forGetter(DailyUse::day),
            Codec.LONG.fieldOf("cooldown_until").forGetter(DailyUse::cooldownUntil)
    ).apply(instance, DailyUse::new));
    // The old streak used game time and cannot be assigned to a calendar day.
    static final Codec<DailyUse> CODEC = Codec.withAlternative(DAILY_CODEC,
            Codec.LONG.fieldOf("last_use_tick").codec().xmap(ignored -> EMPTY, ignored -> 0L));
    static final StreamCodec<ByteBuf, DailyUse> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, DailyUse::count,
            ByteBufCodecs.VAR_LONG, DailyUse::day,
            ByteBufCodecs.VAR_LONG, DailyUse::cooldownUntil,
            DailyUse::new);

    int countOn(long dayTime) {
        return day == Math.floorDiv(dayTime, DAY_TICKS) ? count : 0;
    }

    int cooldownRemaining(long gameTime) {
        return (int) Math.clamp(cooldownUntil - gameTime, 0, Integer.MAX_VALUE);
    }
}
