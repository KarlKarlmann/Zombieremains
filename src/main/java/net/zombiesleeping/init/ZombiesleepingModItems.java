package net.zombiesleeping.init;

import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.common.ForgeSpawnEggItem;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.BlockItem;

import net.zombiesleeping.ZombiesleepingMod;

public class ZombiesleepingModItems {
	public static final DeferredRegister<Item> REGISTRY = DeferredRegister.create(ForgeRegistries.ITEMS, ZombiesleepingMod.MODID);
	public static final RegistryObject<Item> ZOMBIEREMAINS = block(ZombiesleepingModBlocks.ZOMBIEREMAINS);
	public static final RegistryObject<Item> SMOLDERING_ZOMBIEREMAINS = block(ZombiesleepingModBlocks.SMOLDERING_ZOMBIEREMAINS);
	public static final RegistryObject<Item> BURNT_ZOMBIEREMAINS = block(ZombiesleepingModBlocks.BURNT_ZOMBIEREMAINS);
	public static final RegistryObject<Item> SCREAMER_SPAWN_EGG = REGISTRY.register("screamer_spawn_egg", () -> new ForgeSpawnEggItem(ZombiesleepingModEntities.SCREAMER, -1, -1, new Item.Properties()));
	private static RegistryObject<Item> block(RegistryObject<Block> block) {
		return REGISTRY.register(block.getId().getPath(), () -> new BlockItem(block.get(), new Item.Properties()));
	}
}
