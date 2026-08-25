package net.zombiesleeping.procedures;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;
import java.util.stream.Collectors;

@Mod.EventBusSubscriber(modid = "zombiesleeping", bus = Mod.EventBusSubscriber.Bus.MOD)
public class ConfigProcedure {

    // Config Builder
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    
    // Allgemeine Einstellungen
    public static final ForgeConfigSpec.DoubleValue SPAWN_CHANCE;
    public static final ForgeConfigSpec.IntValue PLAYER_DETECTION_RADIUS;
    public static final ForgeConfigSpec.IntValue MAX_LIGHT_LEVEL;
    public static final ForgeConfigSpec.IntValue MAX_LAYERS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> ALLOWED_DIMENSIONS;

    // Performance-Einstellungen fuer die Spawn-Pipeline
    public static final ForgeConfigSpec.IntValue MAX_SPAWNS_PER_TICK;
    public static final ForgeConfigSpec.DoubleValue MAX_TICK_TIME_MS;
    
    public static final ForgeConfigSpec.DoubleValue SCREAMER_SPEED_BUFF_MULTIPLIER;
    
    // Mob-spezifische Einstellungen
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> MOB_LAYER_MAPPINGS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> SPAWN_REQUIREMENTS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> MOB_ENHANCEMENTS;
    
    // NEU: Sterbe-Wahrscheinlichkeiten
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> DEATH_DROP_CHANCES;
    
    public static final ForgeConfigSpec SPEC;
    
    // Runtime-Maps/Sets für schnellen Zugriff
    public static Map<EntityType<?>, Integer> mobToLayersMap = new HashMap<>();
    public static Map<Integer, SpawnConfig> layerToSpawnMap = new HashMap<>();
    public static Map<String, MobEnhancement> mobEnhancements = new HashMap<>();
    public static Set<ResourceLocation> allowedDimensions = new HashSet<>();
    public static Map<EntityType<?>, Double> deathDropChancesMap = new HashMap<>(); // NEU

	public static final ForgeConfigSpec.ConfigValue<List<? extends String>> HAZARD_IMMUNE_MOBS;
    public static Set<EntityType<?>> hazardImmuneMobs = new HashSet<>();
	
	public static final ForgeConfigSpec.ConfigValue<List<? extends String>> DEBUFF_EFFECTS;
	public static final List<ConfiguredEffect> appliedDebuffs = new ArrayList<>();
	public static final ForgeConfigSpec.ConfigValue<List<? extends String>> SCREAMER_TARGET_EFFECTS;
    public static final List<ConfiguredEffect> screamerTargetEffects = new ArrayList<>();
	public static class ConfiguredEffect {
		public final net.minecraft.world.effect.MobEffect effect;
		public final int duration;
		public final int amplifier;

		public ConfiguredEffect(net.minecraft.world.effect.MobEffect effect, int duration, int amplifier) {
			this.effect = effect;
			this.duration = duration;
			this.amplifier = amplifier;
		}
	}
	
    static {
        BUILDER.comment("Zombie Remains Mod Configuration");
        
        BUILDER.push("General Settings");
        SPAWN_CHANCE = BUILDER
            .comment("Chance per random tick that a zombie spawns from remains (0.0 - 1.0)")
            .defineInRange("spawnChance", 0.2, 0.0, 1.0);
            
        PLAYER_DETECTION_RADIUS = BUILDER
            .comment("Radius in blocks to detect nearby players")
            .defineInRange("playerDetectionRadius", 16, 1, 128);
            
        MAX_LIGHT_LEVEL = BUILDER
            .comment("Maximum light level for zombie spawning")
            .defineInRange("maxLightLevel", 7, 0, 15);
            
        MAX_LAYERS = BUILDER
            .comment("Maximum number of layers a remains block can have")
            .defineInRange("maxLayers", 8, 1, 16);

        ALLOWED_DIMENSIONS = BUILDER
            .comment("List of dimension IDs where Zombie Remains are allowed to form.",
                     "Example: 'minecraft:overworld', 'minecraft:the_nether'")
            .defineList("allowedDimensions", Arrays.asList("minecraft:overworld"), obj -> obj instanceof String);

        MAX_SPAWNS_PER_TICK = BUILDER
            .comment("Maximum number of zombies that may be spawned from remains blocks in a single server tick.")
            .defineInRange("maxSpawnsPerTick", 2, 1, 20);

        MAX_TICK_TIME_MS = BUILDER
            .comment("If the average server tick time (in ms) is at or above this value, remains blocks pause")
            .defineInRange("maxTickTimeMsForSpawning", 45.0, 5.0, 200.0);
            
        SCREAMER_SPEED_BUFF_MULTIPLIER = BUILDER
            .comment("The speed multiplier applied to mobs buffed by a screamer's scream.")
            .defineInRange("screamerSpeedBuffMultiplier", 1.1, 1.0, 5.0);
        BUILDER.pop();
        
        BUILDER.push("Mob Layer Mappings");
        MOB_LAYER_MAPPINGS = BUILDER
            .comment("Format: 'mob_id:layers_added'",
                    "Example: 'minecraft:zombie:1' means zombies add 1 layer when they die/despawn")
            .defineList("mobLayerMappings", Arrays.asList(
                "minecraft:zombie:1",
                "zombiesleeping:screamer:1",
                "minecraft:drowned:1",
                "minecraft:husk:1",
                "minecraft:skeleton:2",
                "minecraft:wither_skeleton:3"
            ), obj -> obj instanceof String);
        BUILDER.pop();

		BUILDER.push("Pathfinding Settings");
        HAZARD_IMMUNE_MOBS = BUILDER
            .comment("List of mobs that will ignore the hazard and walk straight through the remains.",
                     "Format: 'namespace:mob_id'")
            .defineList("hazardImmuneMobs", Arrays.asList(
                "minecraft:zombie",
                "minecraft:zombie_villager",
                "minecraft:drowned",
                "minecraft:husk",
                "minecraft:skeleton",
                "minecraft:wither_skeleton",
                "minecraft:stray",
                "zombiesleeping:screamer"
            ), obj -> obj instanceof String);
        BUILDER.pop();
		
        // NEU: Eigene Config für Drop-Chancen beim echten Tod
        BUILDER.push("Death Drop Settings");
        DEATH_DROP_CHANCES = BUILDER
            .comment("Format: 'mob_id:chance'",
                     "Chance between 0.0 and 1.0 for a mob to leave remains ONLY ON DEATH.",
                     "If a mob is NOT listed here, it will never drop remains on death (but still on despawn).",
                     "Example: 'minecraft:zombie:0.25' means a 25% chance.")
            .defineList("deathDropChances", Arrays.asList(
                "minecraft:zombie:0.15",
                "zombiesleeping:screamer:0.5"
            ), obj -> obj instanceof String);
        BUILDER.pop();
        
        BUILDER.push("Spawn Requirements");
        SPAWN_REQUIREMENTS = BUILDER
            .comment("Format: 'min_layers:max_layers:mob_id:weight'")
            .defineList("spawnRequirements", Arrays.asList(
                "1:16:minecraft:zombie:100",
                "1:16:zombiesleeping:screamer:10",
                "2:4:minecraft:husk:20",
                "3:6:minecraft:skeleton:30",
                "5:10:minecraft:zombie:40",
                "7:8:minecraft:skeleton:30",
                "7:16:minecraft:wither_skeleton:20"
            ), obj -> obj instanceof String);
        BUILDER.pop();
        
        BUILDER.push("Mob Enhancements");
        MOB_ENHANCEMENTS = BUILDER
            .comment("Format: 'mob_id:min_layers:health_bonus:damage_bonus:speed_bonus:custom_name'")
            .defineList("mobEnhancements", Arrays.asList(
                "minecraft:zombie:1:0:0:0:", 
                "minecraft:zombie:3:10:1:0:Strong Zombie",
                "minecraft:zombie:5:15:2:0.05:Enhanced Zombie",
                "minecraft:zombie:7:20:3:0.1:Ancient Zombie",
                "minecraft:skeleton:3:8:2:0:Veteran Skeleton",
                "minecraft:skeleton:5:12:3:0.03:Elite Skeleton",
                "minecraft:wither_skeleton:7:25:5:0.05:Cursed Wither"
            ), obj -> obj instanceof String);
        BUILDER.pop();

		BUILDER.push("Debuff Settings");
		DEBUFF_EFFECTS = BUILDER
			.comment("List of debuffs applied when stepping on zombie remains.")
			.defineList("debuffEffects", Arrays.asList(
				"minecraft:slowness:30:1",
				"minecraft:poison:60:0"
			), obj -> obj instanceof String);
		BUILDER.pop();
		
		BUILDER.push("Screamer Settings");
        SCREAMER_TARGET_EFFECTS = BUILDER
            .comment("List of effects applied to the target player when the Screamer screams.",
                     "Format: 'namespace:effect:duration_in_ticks:amplifier'",
                     "Example: 'minecraft:glowing:200:0' applies Glowing for 10 seconds.")
            .defineList("screamerTargetEffects", Arrays.asList(
                "minecraft:glowing:200:0" // 200 Ticks = 10 Sekunden Glowing
            ), obj -> obj instanceof String);
        BUILDER.pop();        
        SPEC = BUILDER.build();
    }

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        loadMobLayerMappings();
        loadSpawnRequirements();
        loadMobEnhancements();
        loadAllowedDimensions();
		loadDebuffEffects();
        loadDeathDropChances();
		loadScreamerEffects();
		loadHazardImmuneMobs();
    }

	private static void loadHazardImmuneMobs() {
        hazardImmuneMobs.clear();
        for (String entry : HAZARD_IMMUNE_MOBS.get()) {
            try {
                ResourceLocation mobId = new ResourceLocation(entry);
                EntityType<?> entityType = ForgeRegistries.ENTITY_TYPES.getValue(mobId);
                if (entityType != null) {
                    hazardImmuneMobs.add(entityType);
                }
            } catch (Exception e) {
                System.err.println("Invalid hazard immune mob config: " + entry);
            }
        }
    }

    public static boolean isMobImmuneToHazard(EntityType<?> mobType) {
        return hazardImmuneMobs.contains(mobType);
    }
	
    private static void loadAllowedDimensions() {
        allowedDimensions.clear();
        for (String dim : ALLOWED_DIMENSIONS.get()) {
            try {
                allowedDimensions.add(new ResourceLocation(dim));
            } catch (Exception e) {
                System.err.println("Invalid dimension id in config: " + dim);
            }
        }
    }

	private static void loadDebuffEffects() {
		appliedDebuffs.clear();
		for (String entry : DEBUFF_EFFECTS.get()) {
			try {
				String[] parts = entry.split(":");
				if (parts.length >= 3) {
					ResourceLocation effectId = new ResourceLocation(parts[0], parts[1]);
					int duration = Integer.parseInt(parts[2]);
					int amplifier = Integer.parseInt(parts[3]);

					net.minecraft.world.effect.MobEffect effect = ForgeRegistries.MOB_EFFECTS.getValue(effectId);
					if (effect != null) {
						appliedDebuffs.add(new ConfiguredEffect(effect, duration, amplifier));
					}
				}
			} catch (Exception e) {
				System.err.println("Invalid debuff effect config: " + entry);
			}
		}
	}

	private static void loadScreamerEffects() {
        screamerTargetEffects.clear();
        for (String entry : SCREAMER_TARGET_EFFECTS.get()) {
            try {
                String[] parts = entry.split(":");
                // Braucht 4 Parts: namespace, effect_name, duration, amplifier
                if (parts.length >= 4) {
                    ResourceLocation effectId = new ResourceLocation(parts[0], parts[1]);
                    int duration = Integer.parseInt(parts[2]);
                    int amplifier = Integer.parseInt(parts[3]);

                    net.minecraft.world.effect.MobEffect effect = ForgeRegistries.MOB_EFFECTS.getValue(effectId);
                    if (effect != null) {
                        screamerTargetEffects.add(new ConfiguredEffect(effect, duration, amplifier));
                    }
                }
            } catch (Exception e) {
                System.err.println("Invalid screamer target effect config: " + entry);
            }
        }
    }
    
    public static boolean isDimensionAllowed(ResourceLocation dimensionId) {
        return allowedDimensions.contains(dimensionId);
    }

    private static void loadMobLayerMappings() {
        mobToLayersMap.clear();
        for (String mapping : MOB_LAYER_MAPPINGS.get()) {
            try {
                String[] parts = mapping.split(":");
                if (parts.length >= 3) {
                    ResourceLocation mobId = new ResourceLocation(parts[0], parts[1]);
                    int layers = Integer.parseInt(parts[2]);
                    EntityType<?> entityType = ForgeRegistries.ENTITY_TYPES.getValue(mobId);
                    if (entityType != null) {
                        mobToLayersMap.put(entityType, layers);
                    }
                }
            } catch (Exception e) {
                System.err.println("Invalid mob layer mapping: " + mapping);
            }
        }
    }

    // NEU: Lade Sterbe-Chancen aus der Config
    private static void loadDeathDropChances() {
        deathDropChancesMap.clear();
        for (String mapping : DEATH_DROP_CHANCES.get()) {
            try {
                String[] parts = mapping.split(":");
                if (parts.length >= 3) {
                    ResourceLocation mobId = new ResourceLocation(parts[0], parts[1]);
                    double chance = Double.parseDouble(parts[2]);
                    EntityType<?> entityType = ForgeRegistries.ENTITY_TYPES.getValue(mobId);
                    if (entityType != null) {
                        deathDropChancesMap.put(entityType, chance);
                    }
                }
            } catch (Exception e) {
                System.err.println("Invalid death drop chance mapping: " + mapping);
            }
        }
    }

    private static void loadSpawnRequirements() {
        layerToSpawnMap.clear();
        Map<Integer, List<WeightedMob>> tempMap = new HashMap<>();
        for (String requirement : SPAWN_REQUIREMENTS.get()) {
            try {
                String[] parts = requirement.split(":");
                if (parts.length >= 5) {
                    int minLayers = Integer.parseInt(parts[0]);
                    int maxLayers = Integer.parseInt(parts[1]);
                    ResourceLocation mobId = new ResourceLocation(parts[2], parts[3]);
                    int weight = Integer.parseInt(parts[4]);
                    EntityType<?> entityType = ForgeRegistries.ENTITY_TYPES.getValue(mobId);
                    if (entityType != null) {
                        for (int layer = minLayers; layer <= maxLayers; layer++) {
                            tempMap.computeIfAbsent(layer, k -> new ArrayList<>())
                                .add(new WeightedMob(entityType, weight));
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("Invalid spawn requirement: " + requirement);
            }
        }
        for (Map.Entry<Integer, List<WeightedMob>> entry : tempMap.entrySet()) {
            layerToSpawnMap.put(entry.getKey(), new SpawnConfig(entry.getValue()));
        }
    }

    private static void loadMobEnhancements() {
        mobEnhancements.clear();
        for (String enhancement : MOB_ENHANCEMENTS.get()) {
            try {
                String[] parts = enhancement.split(":");
                if (parts.length >= 6) {
                    String mobKey = parts[0] + ":" + parts[1];
                    int minLayers = Integer.parseInt(parts[2]);
                    float healthBonus = Float.parseFloat(parts[3]);
                    float damageBonus = Float.parseFloat(parts[4]);
                    float speedBonus = Float.parseFloat(parts[5]);
                    String customName = parts.length > 6 ? parts[6] : "";
                    mobEnhancements.put(mobKey, 
                        new MobEnhancement(minLayers, healthBonus, damageBonus, speedBonus, customName));
                }
            } catch (Exception e) {
                System.err.println("Invalid mob enhancement: " + enhancement);
            }
        }
    }

    public static int getLayersForMob(EntityType<?> mobType) {
        return mobToLayersMap.getOrDefault(mobType, 0);
    }
    
    // NEU: Helper-Methode für die Sterbe-Chance
    public static double getDeathDropChance(EntityType<?> mobType) {
        return deathDropChancesMap.getOrDefault(mobType, -1.0); // Gibt -1.0 zurück, wenn nicht in Liste
    }

    public static EntityType<?> getRandomMobForLayers(int layers, Random random) {
        SpawnConfig config = layerToSpawnMap.get(layers);
        return config != null ? config.getRandomMob(random) : null;
    }

    public static MobEnhancement getEnhancementForMob(EntityType<?> mobType, int layers) {
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(mobType);
        if (id == null) return null;
        String key = id.toString();
        MobEnhancement enhancement = mobEnhancements.get(key);
        if (enhancement != null && layers >= enhancement.minLayers) {
            return enhancement;
        }
        return null;
    }

    public static class WeightedMob {
        public final EntityType<?> mobType;
        public final int weight;
        public WeightedMob(EntityType<?> mobType, int weight) {
            this.mobType = mobType;
            this.weight = weight;
        }
    }

    public static class SpawnConfig {
        private final List<WeightedMob> mobs;
        private final int totalWeight;
        public SpawnConfig(List<WeightedMob> mobs) {
            this.mobs = mobs;
            this.totalWeight = mobs.stream().mapToInt(m -> m.weight).sum();
        }
        public EntityType<?> getRandomMob(Random random) {
            if (mobs.isEmpty() || totalWeight <= 0) return null;
            int randomWeight = random.nextInt(totalWeight);
            int currentWeight = 0;
            for (WeightedMob mob : mobs) {
                currentWeight += mob.weight;
                if (randomWeight < currentWeight) {
                    return mob.mobType;
                }
            }
            return mobs.get(mobs.size() - 1).mobType;
        }
    }

    public static class MobEnhancement {
        public final int minLayers;
        public final float healthBonus;
        public final float damageBonus;
        public final float speedBonus;
        public final String customName;
        public MobEnhancement(int minLayers, float healthBonus, float damageBonus, float speedBonus, String customName) {
            this.minLayers = minLayers;
            this.healthBonus = healthBonus;
            this.damageBonus = damageBonus;
            this.speedBonus = speedBonus;
            this.customName = customName;
        }
    }
}