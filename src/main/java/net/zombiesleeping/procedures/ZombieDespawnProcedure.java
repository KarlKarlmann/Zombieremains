package net.zombiesleeping.procedures;

import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import net.zombiesleeping.init.ZombiesleepingModBlocks;
import net.zombiesleeping.block.ZombieremainsBlock;
import net.zombiesleeping.procedures.ConfigProcedure;

@Mod.EventBusSubscriber
public class ZombieDespawnProcedure {

    // Event für natürliches Despawnen (unverändert)
    @SubscribeEvent
    public static void onMobRemoved(EntityLeaveLevelEvent event) {
        if (!(event.getEntity() instanceof LivingEntity)) return;
        LivingEntity livingEntity = (LivingEntity) event.getEntity();
        if (livingEntity.level().isClientSide()) return;
        if (!(livingEntity.level() instanceof ServerLevel serverLevel)) return;

        if (!ConfigProcedure.isDimensionAllowed(serverLevel.dimension().location())) return;

        if (livingEntity.getHealth() <= 0) return;

        int layersToAdd = ConfigProcedure.getLayersForMob(livingEntity.getType());
        if (layersToAdd <= 0) return;

        BlockPos pos = livingEntity.blockPosition();

        if (!serverLevel.isLoaded(pos)) return;
        java.util.concurrent.CompletableFuture.delayedExecutor(50, java.util.concurrent.TimeUnit.MILLISECONDS)
            .execute(() -> {
                serverLevel.getServer().execute(() -> {
                    try {
                        placeOrAddZombieRemains(serverLevel, pos, layersToAdd);
                    } catch (Exception e) {
                        System.err.println("Fehler beim Setzen des Zombieremains-Blocks: " + e.getMessage());
                    }
                });
            });
    }

    // Event für Mob-Tod (NEU: Greift auf config-gesteuerte Wahrscheinlichkeiten zurück)
    @SubscribeEvent
    public static void onMobDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof LivingEntity)) return;
        LivingEntity livingEntity = (LivingEntity) event.getEntity();
        if (livingEntity.level().isClientSide()) return;
        if (!(livingEntity.level() instanceof ServerLevel serverLevel)) return;

        if (!ConfigProcedure.isDimensionAllowed(serverLevel.dimension().location())) return;

        // NEU: Abfragen der Drop-Chance aus der neuen Death Drop Config
        double dropChance = ConfigProcedure.getDeathDropChance(livingEntity.getType());
        
        // Wenn Wert kleiner 0 ist, ist der Mob nicht in der Config gelistet -> kein Drop
        if (dropChance < 0) return; 
        
        // Zufallswurf: Wenn die generierte Zahl größer oder gleich der Chance ist, wird abgebrochen
        if (serverLevel.getRandom().nextFloat() >= dropChance) return;

        // Wir nutzen weiterhin die Ebenen-Anzahl, die dem Mob zugewiesen wurde
        int layersToAdd = ConfigProcedure.getLayersForMob(livingEntity.getType());
        if (layersToAdd <= 0) return;

        BlockPos pos = livingEntity.blockPosition();

        java.util.concurrent.CompletableFuture.delayedExecutor(100, java.util.concurrent.TimeUnit.MILLISECONDS)
            .execute(() -> {
                serverLevel.getServer().execute(() -> {
                    try {
                        placeOrAddZombieRemains(serverLevel, pos, layersToAdd);
                    } catch (Exception e) {
                        System.err.println("Fehler beim Setzen des Zombieremains-Blocks: " + e.getMessage());
                    }
                });
            });
    }

    private static void placeOrAddZombieRemains(ServerLevel world, BlockPos pos, int layersToAdd) {
        if (!world.isLoaded(pos)) return;

        BlockState currentState = world.getBlockState(pos);

        if (currentState.isAir()) {
            int initialLayers = Math.min(layersToAdd, ConfigProcedure.MAX_LAYERS.get());
            world.setBlock(pos, 
                ZombiesleepingModBlocks.ZOMBIEREMAINS.get().defaultBlockState()
                    .setValue(ZombieremainsBlock.LAYERS, initialLayers), 
                3);
        }

        else if (currentState.getBlock() instanceof ZombieremainsBlock) {
            ZombieremainsBlock.addLayer(world, pos, layersToAdd);
        }

        else {
            BlockPos below = pos.below();
            BlockState belowState = world.getBlockState(below);
            
            if (belowState.getBlock() instanceof ZombieremainsBlock) {
                ZombieremainsBlock.addLayer(world, below, layersToAdd);
            } else if (belowState.isAir()) {
                int initialLayers = Math.min(layersToAdd, ConfigProcedure.MAX_LAYERS.get());
                world.setBlock(below, 
                    ZombiesleepingModBlocks.ZOMBIEREMAINS.get().defaultBlockState()
                        .setValue(ZombieremainsBlock.LAYERS, initialLayers), 
                    3);
            }
        }
    }
}