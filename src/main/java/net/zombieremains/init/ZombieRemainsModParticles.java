package net.zombieremains.init;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.zombieremains.ZombieRemainsMod;

public class ZombieRemainsModParticles {
    public static final DeferredRegister<ParticleType<?>> REGISTRY = DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, ZombieRemainsMod.MODID);
    
    // Registriert unseren einfachen Partikel
    public static final RegistryObject<SimpleParticleType> ZOMBIE_HAND = REGISTRY.register("zombie_hand", () -> new SimpleParticleType(true));
}