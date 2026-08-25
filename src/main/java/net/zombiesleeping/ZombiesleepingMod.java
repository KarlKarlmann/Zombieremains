package net.zombiesleeping;

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

import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.FriendlyByteBuf;

import net.zombiesleeping.procedures.ConfigProcedure;
import net.zombiesleeping.init.ZombiesleepingModTabs;
import net.zombiesleeping.init.ZombiesleepingModSounds;
import net.zombiesleeping.init.ZombiesleepingModItems;
import net.zombiesleeping.init.ZombiesleepingModEntities;
import net.zombiesleeping.init.ZombiesleepingModBlocks;

import java.util.function.Supplier;
import java.util.function.Function;
import java.util.function.BiConsumer;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.List;
import java.util.Collection;
import java.util.ArrayList;
import java.util.AbstractMap;

@Mod("zombiesleeping")
public class ZombiesleepingMod {
	public static final Logger LOGGER = LogManager.getLogger(ZombiesleepingMod.class);
	public static final String MODID = "zombiesleeping";

	public ZombiesleepingMod() {

		MinecraftForge.EVENT_BUS.register(this);
		IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
		ZombiesleepingModSounds.REGISTRY.register(bus);
		ZombiesleepingModBlocks.REGISTRY.register(bus);
		ZombiesleepingModItems.REGISTRY.register(bus);
		ZombiesleepingModEntities.REGISTRY.register(bus);
		ZombiesleepingModTabs.REGISTRY.register(bus);
		net.zombiesleeping.init.ZombiesleepingModParticles.REGISTRY.register(bus); // NEU: Partikel Registry

		ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, ConfigProcedure.SPEC);

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
}