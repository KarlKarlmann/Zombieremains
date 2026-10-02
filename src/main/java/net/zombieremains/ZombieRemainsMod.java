package net.zombieremains;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.fml.util.thread.SidedThreadGroups;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.registries.MissingMappingsEvent;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.FriendlyByteBuf;

import net.zombieremains.procedures.ConfigProcedure;
import net.zombieremains.init.ZombieRemainsModTabs;
import net.zombieremains.init.ZombieRemainsModSounds;
import net.zombieremains.init.ZombieRemainsModItems;
import net.zombieremains.init.ZombieRemainsModEntities;
import net.zombieremains.init.ZombieRemainsModBlocks;

import java.util.function.Supplier;
import java.util.function.Function;
import java.util.function.BiConsumer;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.List;
import java.util.Collection;
import java.util.ArrayList;
import java.util.AbstractMap;

@Mod("zombieremains")
public class ZombieRemainsMod {
	public static final Logger LOGGER = LogManager.getLogger(ZombieRemainsMod.class);
	public static final String MODID = "zombieremains";

	public ZombieRemainsMod() {

		MinecraftForge.EVENT_BUS.register(this);
		IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
		ZombieRemainsModSounds.REGISTRY.register(bus);
		ZombieRemainsModBlocks.REGISTRY.register(bus);
		ZombieRemainsModItems.REGISTRY.register(bus);
		ZombieRemainsModEntities.REGISTRY.register(bus);
		ZombieRemainsModTabs.REGISTRY.register(bus);
		net.zombieremains.init.ZombieRemainsModParticles.REGISTRY.register(bus); // NEU: Partikel Registry

		ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, ConfigProcedure.SPEC, "zombieremains-common-v2.toml");

	}

	private static final String PROTOCOL_VERSION = "1";
	public static final SimpleChannel PACKET_HANDLER = NetworkRegistry.newSimpleChannel(new ResourceLocation(MODID, MODID), () -> PROTOCOL_VERSION, PROTOCOL_VERSION::equals, PROTOCOL_VERSION::equals);
	private static int messageID = 0;

	public static <T> void addNetworkMessage(Class<T> messageType, BiConsumer<T, FriendlyByteBuf> encoder, Function<FriendlyByteBuf, T> decoder, BiConsumer<T, Supplier<NetworkEvent.Context>> messageConsumer) {
		PACKET_HANDLER.registerMessage(messageID, messageType, encoder, decoder, messageConsumer);
		messageID++;
	}

	private static final Collection<AbstractMap.SimpleEntry<Runnable, Integer>> workQueue = new ConcurrentLinkedQueue<>();

	public static void queueServerWork(int tick, Runnable action) {
		if (Thread.currentThread().getThreadGroup() == SidedThreadGroups.SERVER)
			workQueue.add(new AbstractMap.SimpleEntry<>(action, tick));
	}

	// NEU: sehr leichtgewichtiges Tick-Zeit-Tracking (einmal pro Server-Tick,
	// nicht pro Block!). Dient als billiges "geht's dem Server noch gut?"-Signal
	// fuer die Zombie-Spawn-Pipeline.
	private static long tickStartNanos = 0L;
	private static volatile double avgTickTimeMs = 25.0; // optimistischer Startwert vor der ersten Messung

	@SubscribeEvent
	public void tick(TickEvent.ServerTickEvent event) {
		if (event.phase == TickEvent.Phase.START) {
			tickStartNanos = System.nanoTime();
			return;
		}

		// ab hier: Phase.END
		double tickTimeMs = (System.nanoTime() - tickStartNanos) / 1_000_000.0;
		// Exponentiell gleitender Durchschnitt: reagiert auf anhaltende Last,
		// aber nicht auf jeden einzelnen kurzen Ausreisser
		avgTickTimeMs = avgTickTimeMs * 0.9 + tickTimeMs * 0.1;

		List<AbstractMap.SimpleEntry<Runnable, Integer>> actions = new ArrayList<>();
		workQueue.forEach(work -> {
			work.setValue(work.getValue() - 1);
			if (work.getValue() == 0)
				actions.add(work);
		});
		actions.forEach(e -> e.getKey().run());
		workQueue.removeAll(actions);

		// NEU: Zombie-Spawn-Pipeline pro Tick abarbeiten (gedrosselt, siehe dort)
		ZombieSpawnPipeline.tick();
	}
	public static boolean hasSpawnBudget() {
		return avgTickTimeMs < ConfigProcedure.MAX_TICK_TIME_MS.get();
	}
	//rebranding ^^
	@SubscribeEvent
	public void onMissingMappings(MissingMappingsEvent event) {
		event.getMappings(ForgeRegistries.Keys.BLOCKS, "zombiesleeping").forEach(mapping -> {
			switch (mapping.getKey().getPath()) {
				case "zombieremains" -> 
					mapping.remap(ZombieRemainsModBlocks.ZOMBIEREMAINS.get());
				case "smoldering_zombieremains" -> 
					mapping.remap(ZombieRemainsModBlocks.SMOLDERING_ZOMBIEREMAINS.get());
				case "burnt_zombieremains" -> 
					mapping.remap(ZombieRemainsModBlocks.BURNT_ZOMBIEREMAINS.get());
			}
		});
	}
}