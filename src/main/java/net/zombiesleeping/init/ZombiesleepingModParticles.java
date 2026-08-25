package net.zombiesleeping.init;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.zombiesleeping.ZombiesleepingMod;

public class ZombiesleepingModParticles {
    public static final DeferredRegister<ParticleType<?>> REGISTRY = DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, ZombiesleepingMod.MODID);
    
    // Registriert unseren einfachen Partikel
    public static final RegistryObject<SimpleParticleType> ZOMBIE_HAND = REGISTRY.register("zombie_hand", () -> new SimpleParticleType(true));
}