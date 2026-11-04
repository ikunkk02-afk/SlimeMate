package com.shouyun.slimemate;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.ColorParticleOption;

final class DropletParticle extends TextureSheetParticle {
    private final float opacity;

    private DropletParticle(ColorParticleOption options, ClientLevel level, double x, double y, double z,
                            double vx, double vy, double vz, SpriteSet sprites) {
        super(level, x, y, z);
        setParticleSpeed(vx, vy, vz);
        setColor(options.getRed(), options.getGreen(), options.getBlue());
        opacity = options.getAlpha();
        setAlpha(opacity);
        gravity = 0.48F;
        friction = 0.94F;
        lifetime = 9 + random.nextInt(5);
        quadSize = 0.055F + random.nextFloat() * 0.04F;
        pickSprite(sprites);
    }

    static ParticleProvider<ColorParticleOption> provider(SpriteSet sprites) {
        return (options, level, x, y, z, vx, vy, vz) -> new DropletParticle(options, level, x, y, z, vx, vy, vz, sprites);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public void tick() {
        super.tick();
        setAlpha(opacity * Math.min(1.0F, (lifetime - age) / 4.0F));
    }
}
