package net.zombieremains.init;

import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.common.ForgeSpawnEggItem;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.BlockItem;

import net.zombieremains.ZombieRemainsMod;

public class ZombieRemainsModItems {
	public static final DeferredRegister<Item> REGISTRY = DeferredRegister.create(ForgeRegistries.ITEMS, ZombieRemainsMod.MODID);
	public static final RegistryObject<Item> ZOMBIEREMAINS = block(ZombieRemainsModBlocks.ZOMBIEREMAINS);
	public static final RegistryObject<Item> SMOLDERING_ZOMBIEREMAINS = block(ZombieRemainsModBlocks.SMOLDERING_ZOMBIEREMAINS);
	public static final RegistryObject<Item> BURNT_ZOMBIEREMAINS = block(ZombieRemainsModBlocks.BURNT_ZOMBIEREMAINS);
	public static final RegistryObject<Item> SCREAMER_SPAWN_EGG = REGISTRY.register("screamer_spawn_egg", () -> new ForgeSpawnEggItem(ZombieRemainsModEntities.SCREAMER, -1, -1, new Item.Properties()));
	private static RegistryObject<Item> block(RegistryObject<Block> block) {
		return REGISTRY.register(block.getId().getPath(), () -> new BlockItem(block.get(), new Item.Properties()));
	}
}
