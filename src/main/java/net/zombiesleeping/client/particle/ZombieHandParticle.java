package net.zombiesleeping.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class ZombieHandParticle extends TextureSheetParticle {
    
    protected ZombieHandParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet spriteSet) {
        super(level, x, y, z);
        this.friction = 0.9F;
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.quadSize *= 1.2F; // Macht den Partikel etwas größer
        this.lifetime = 20 + this.random.nextInt(15); // Lebt für ca. 1 bis 1,5 Sekunden
        this.setSpriteFromAge(spriteSet);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT; // Erlaubt Transparenz im Bild
    }

    @Override
    public void tick() {
        super.tick();
        // Lässt den Partikel langsam ausblenden (Alpha-Wert von 1.0 zu 0.0)
        this.setAlpha(1.0F - ((float)this.age / (float)this.lifetime));
    }

    @OnlyIn(Dist.CLIENT)
    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet spriteSet;

        public Provider(SpriteSet spriteSet) {
            this.spriteSet = spriteSet;
        }

        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
            ZombieHandParticle particle = new ZombieHandParticle(level, x, y, z, vx, vy, vz, this.spriteSet);
            particle.pickSprite(this.spriteSet);
            return particle;
        }
    }
}