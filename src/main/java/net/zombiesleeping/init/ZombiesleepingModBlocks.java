package net.zombiesleeping.init;

import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FireBlock;

import net.zombiesleeping.block.ZombieremainsBlock;
import net.zombiesleeping.block.SmolderingZombieremainsBlock;
import net.zombiesleeping.block.BurntZombieremainsBlock;
import net.zombiesleeping.ZombiesleepingMod;

import java.lang.reflect.Method;

@Mod.EventBusSubscriber(modid = ZombiesleepingMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ZombiesleepingModBlocks {
	public static final DeferredRegister<Block> REGISTRY = DeferredRegister.create(ForgeRegistries.BLOCKS, ZombiesleepingMod.MODID);
	public static final RegistryObject<Block> ZOMBIEREMAINS = REGISTRY.register("zombieremains", () -> new ZombieremainsBlock());
	public static final RegistryObject<Block> SMOLDERING_ZOMBIEREMAINS = REGISTRY.register("smoldering_zombieremains", () -> new SmolderingZombieremainsBlock());
	public static final RegistryObject<Block> BURNT_ZOMBIEREMAINS = REGISTRY.register("burnt_zombieremains", () -> new BurntZombieremainsBlock());

	@SubscribeEvent
	public static void onCommonSetup(FMLCommonSetupEvent event) {
		event.enqueueWork(() -> {
			FireBlock fireBlock = (FireBlock) Blocks.FIRE;
			registerFlammable(fireBlock, ZOMBIEREMAINS.get(), 60, 30);
			registerFlammable(fireBlock, SMOLDERING_ZOMBIEREMAINS.get(), 60, 30);
		});
	}

	private static void registerFlammable(FireBlock fireBlock, Block block, int encouragement, int flammability) {
		try {
			Method method;
			try {
				method = FireBlock.class.getDeclaredMethod("setFlammable", Block.class, int.class, int.class);
			} catch (NoSuchMethodException e) {
				// Fallback auf den obfuszierte SRG-Namen für kompilierte Builds
				method = FireBlock.class.getDeclaredMethod("m_53444_", Block.class, int.class, int.class);
			}
			method.setAccessible(true);
			method.invoke(fireBlock, block, encouragement, flammability);
		} catch (Exception e) {
			ZombiesleepingMod.LOGGER.error("Fehler beim Registrieren der Brennbarkeit für Block: {}", block, e);
		}
	}
}