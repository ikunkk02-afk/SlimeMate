package com.shouyun.slimemate;

import net.neoforged.neoforge.common.ModConfigSpec;

final class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    static final int DEFAULT_COOLDOWN_SECONDS = 60;

    static final ModConfigSpec.IntValue COOLDOWN_SECONDS = BUILDER
            .comment("Shared cooldown for both items, in seconds. Applies after each completed use. Set to 0 to disable.")
            .translation("slimemate.configuration.cooldownSeconds")
            .defineInRange("cooldownSeconds", DEFAULT_COOLDOWN_SECONDS, 0, 3600);
    static final ModConfigSpec.IntValue POSITIVE_USES = BUILDER
            .comment("Number of uses per game day that grant a positive effect, capped below dailyOveruseFrom.")
            .defineInRange("dailyPositiveUses", 3, 0, 9);
    static final ModConfigSpec.IntValue OVERUSE_FROM = BUILDER
            .comment("First use that causes fatigue until the next game day.")
            .defineInRange("dailyOveruseFrom", 7, 1, 10);
    static final ModConfigSpec.DoubleValue BLEED_CHANCE = BUILDER
            .comment("Bleeding probability per completed use, starting at the tenth use of the day.")
            .defineInRange("bleedChance", 0.25, 0, 1);
    static final ModConfigSpec.DoubleValue FAINT_CHANCE = BUILDER
            .comment("Fainting probability per completed use, starting at the eleventh use of the day.")
            .defineInRange("faintChance", 0.25, 0, 1);
    static final ModConfigSpec.EnumValue<TimeSkip> TIME_SKIP = BUILDER
            .comment("Skip half a day or a full day in the Overworld on recovery, subject to doDaylightCycle.")
            .defineEnum("timeSkip", TimeSkip.SINGLE_PLAYER);
    static final ModConfigSpec SPEC = BUILDER.build();

    enum TimeSkip { SINGLE_PLAYER, ALWAYS, NEVER }

    private Config() {}
}
