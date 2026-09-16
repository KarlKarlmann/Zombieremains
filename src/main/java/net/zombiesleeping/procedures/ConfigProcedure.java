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
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> SPAWN_RULES;
    
    // Sterbe-Wahrscheinlichkeiten
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> DEATH_DROP_CHANCES;

    // Smoldering Einstellungen
    public static final ForgeConfigSpec.DoubleValue SMOLDERING_SPAWN_CHANCE;
    public static final ForgeConfigSpec.DoubleValue SMOLDERING_RESOLVE_CHANCE;
    public static final ForgeConfigSpec.DoubleValue SMOLDERING_CHANCE_TO_COOL_DOWN;
    public static final ForgeConfigSpec.DoubleValue SMOLDERING_FIRE_SPREAD_CHANCE;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> SMOLDERING_SPAWN_RULES;
    
    public static final ForgeConfigSpec SPEC;
    
    // Runtime-Maps/Sets für schnellen Zugriff
    public static Map<EntityType<?>, Integer> mobToLayersMap = new HashMap<>();
    public static Map<Integer, SpawnConfig> layerToSpawnMap = new HashMap<>();
    public static Set<ResourceLocation> allowedDimensions = new HashSet<>();
    public static Map<EntityType<?>, Double> deathDropChancesMap = new HashMap<>(); 
    public static Map<Integer, SpawnConfig> layerToSmolderingSpawnMap = new HashMap<>(); 

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
        
        BUILDER.push("Spawn Rules");
        SPAWN_RULES = BUILDER
            .comment("Format: 'namespace:id;min_layers;max_layers;weight;health_bonus;damage_bonus;speed_bonus;custom_name;custom_nbt'",
                     "Combines spawn requirements and mob enhancements into one easy list.",
                     "Empty custom_name or custom_nbt is allowed. Leave empty between semicolons.")
            .defineList("spawnRules", Arrays.asList(
                "minecraft:zombie;1;16;100;0;0;0;;",
                "zombiesleeping:screamer;1;16;10;0;0;0;;",
                "minecraft:husk;2;4;20;0;0;0;;",
                "minecraft:skeleton;3;6;30;0;0;0;;",
                "minecraft:zombie;3;16;20;10;1;0;Strong Zombie;",
                "minecraft:skeleton;3;8;15;8;2;0;Veteran Skeleton;",
                "minecraft:zombie;5;10;40;15;2;0.05;Enhanced Zombie;",
                "minecraft:skeleton;5;12;10;12;3;0.03;Elite Skeleton;",
                "minecraft:zombie;7;16;20;20;3;0.1;Ancient Zombie;",
                "minecraft:skeleton;7;8;30;0;0;0;;",
                "minecraft:wither_skeleton;7;16;20;25;5;0.05;Cursed Wither;"
            ), obj -> obj instanceof String);
        BUILDER.pop();

        BUILDER.push("Smoldering Block Settings");
        SMOLDERING_SPAWN_CHANCE = BUILDER
            .comment("Chance that a smoldering remains block spawns a special mob when it cools down (0.0 - 1.0)")
            .defineInRange("smolderingSpawnChance", 0.15, 0.0, 1.0);

        SMOLDERING_RESOLVE_CHANCE = BUILDER
            .comment("Chance PER RANDOM TICK that a smoldering block decides its final fate (cool down or destroy). Default is 0.005 (0.5%)")
            .defineInRange("smolderingResolveChance", 0.005, 0.0001, 1.0);

        SMOLDERING_CHANCE_TO_COOL_DOWN = BUILDER
            .comment("When resolving, this is the chance (0.0 - 1.0) that the block cools into permanent Burnt Remains. Otherwise, it is completely destroyed. Default is 0.8 (80%)")
            .defineInRange("smolderingChanceToCoolDown", 0.8, 0.0, 1.0);

        SMOLDERING_FIRE_SPREAD_CHANCE = BUILDER
            .comment("Chance PER RANDOM TICK that the smoldering block spreads actual fire to a neighboring block. Default is 0.05 (5%)")
            .defineInRange("smolderingFireSpreadChance", 0.05, 0.0, 1.0);
            
        SMOLDERING_SPAWN_RULES = BUILDER
            .comment("Format: 'namespace:id;min_layers;max_layers;weight;health_bonus;damage_bonus;speed_bonus;custom_name;custom_nbt'")
            .defineList("smolderingSpawnRules", Arrays.asList(
                "minecraft:zombie;1;16;100;0;0;0;Burnt Zombie;{Fire:160s,IsBaby:1b}",
                "minecraft:wither_skeleton;3;16;50;10;2;0.05;Charred Wither;{Fire:160s}"
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
        loadSpawnRules();
        loadAllowedDimensions();
        loadDebuffEffects();
        loadDeathDropChances();
        loadScreamerEffects();
        loadHazardImmuneMobs();
        loadSmolderingSpawnRules();
    }

    private static void loadSmolderingSpawnRules() {
        layerToSmolderingSpawnMap.clear();
        Map<Integer, List<SpawnRule>> tempMap = new HashMap<>();
        for (String ruleStr : SMOLDERING_SPAWN_RULES.get()) {
            parseAndAddRule(ruleStr, tempMap);
        }
        for (Map.Entry<Integer, List<SpawnRule>> entry : tempMap.entrySet()) {
            layerToSmolderingSpawnMap.put(entry.getKey(), new SpawnConfig(entry.getValue()));
        }
    }

    private static void loadSpawnRules() {
        layerToSpawnMap.clear();
        Map<Integer, List<SpawnRule>> tempMap = new HashMap<>();
        for (String ruleStr : SPAWN_RULES.get()) {
            parseAndAddRule(ruleStr, tempMap);
        }
        for (Map.Entry<Integer, List<SpawnRule>> entry : tempMap.entrySet()) {
            layerToSpawnMap.put(entry.getKey(), new SpawnConfig(entry.getValue()));
        }
    }

    private static void parseAndAddRule(String ruleStr, Map<Integer, List<SpawnRule>> tempMap) {
        try {
            // -1 erlaubt uns leere Eintraege (wie beim Custom Name oder NBT) korrekt als leere Strings zu erfassen
            String[] parts = ruleStr.split(";", -1);
            if (parts.length >= 8) { // Minimum bis custom_name
                ResourceLocation mobId = new ResourceLocation(parts[0]);
                int minLayers = Integer.parseInt(parts[1]);
                int maxLayers = Integer.parseInt(parts[2]);
                int weight = Integer.parseInt(parts[3]);
                float healthBonus = Float.parseFloat(parts[4]);
                float damageBonus = Float.parseFloat(parts[5]);
                float speedBonus = Float.parseFloat(parts[6]);
                String customName = parts[7];
                
                String customNbt = "";
                if (parts.length > 8) {
                    customNbt = parts[8];
                }
                
                EntityType<?> entityType = ForgeRegistries.ENTITY_TYPES.getValue(mobId);
                if (entityType != null) {
                    SpawnRule rule = new SpawnRule(entityType, minLayers, maxLayers, weight, healthBonus, damageBonus, speedBonus, customName, customNbt);
                    for (int layer = minLayers; layer <= maxLayers; layer++) {
                        tempMap.computeIfAbsent(layer, k -> new ArrayList<>()).add(rule);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Invalid spawn rule: " + ruleStr);
        }
    }

    public static SpawnRule getRandomSmolderingSpawnRuleForLayers(int layers, java.util.Random random) {
        SpawnConfig config = layerToSmolderingSpawnMap.get(layers);
        return config != null ? config.getRandomRule(random) : null;
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

    public static boolean isDimensionAllowed(ResourceLocation dimensionId) {
        return allowedDimensions.contains(dimensionId);
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

    public static int getLayersForMob(EntityType<?> mobType) {
        return mobToLayersMap.getOrDefault(mobType, 0);
    }
    
    public static double getDeathDropChance(EntityType<?> mobType) {
        return deathDropChancesMap.getOrDefault(mobType, -1.0); 
    }

    public static SpawnRule getRandomSpawnRuleForLayers(int layers, Random random) {
        SpawnConfig config = layerToSpawnMap.get(layers);
        return config != null ? config.getRandomRule(random) : null;
    }

    public static void applyEnhancements(net.minecraft.world.entity.Mob mob, SpawnRule rule, int currentLayers) {
        if (rule == null) return;

        int layerMultiplier = currentLayers - rule.minLayers + 1;
        if (layerMultiplier < 1) layerMultiplier = 1;

        if (rule.healthBonus > 0 && mob.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH) != null) {
            float bonusHealth = rule.healthBonus * layerMultiplier;
            double newMaxHealth = mob.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).getBaseValue() + bonusHealth;
            mob.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(newMaxHealth);
            mob.setHealth((float) newMaxHealth);
        }

        if (rule.damageBonus > 0 && mob.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE) != null) {
            float bonusDamage = rule.damageBonus * layerMultiplier;
            double newDamage = mob.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE).getBaseValue() + bonusDamage;
            mob.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE).setBaseValue(newDamage);
        }

        if (rule.speedBonus > 0 && mob.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED) != null) {
            float bonusSpeed = rule.speedBonus * layerMultiplier;
            double newSpeed = mob.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED).getBaseValue() + bonusSpeed;
            mob.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED).setBaseValue(newSpeed);
        }

        if (rule.customName != null && !rule.customName.isEmpty()) {
            String colorCode = currentLayers >= 7 ? "§4" : currentLayers >= 5 ? "§6" : "§a";
            mob.setCustomName(net.minecraft.network.chat.Component.literal(colorCode + rule.customName));
            mob.setCustomNameVisible(true);
        }
        
        // NBT-Magie! Wir mergen das NBT aus der Config in das Entity
        if (rule.customNbt != null && !rule.customNbt.isEmpty()) {
            try {
                net.minecraft.nbt.CompoundTag nbtToMerge = net.minecraft.nbt.TagParser.parseTag(rule.customNbt);
                net.minecraft.nbt.CompoundTag mobNbt = new net.minecraft.nbt.CompoundTag();
                mob.saveWithoutId(mobNbt); // Lese aktuellen Zustand aus
                mobNbt.merge(nbtToMerge);  // Überschreibe mit Config-Werten
                mob.load(mobNbt);          // Lade Zustand ins Entity zurück
            } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
                System.err.println("Invalid NBT string in config for mob rule: " + rule.customNbt);
            }
        }
    }

    public static class SpawnRule {
        public final EntityType<?> mobType;
        public final int minLayers;
        public final int maxLayers;
        public final int weight;
        public final float healthBonus;
        public final float damageBonus;
        public final float speedBonus;
        public final String customName;
        public final String customNbt;

        public SpawnRule(EntityType<?> mobType, int minLayers, int maxLayers, int weight, float healthBonus, float damageBonus, float speedBonus, String customName, String customNbt) {
            this.mobType = mobType;
            this.minLayers = minLayers;
            this.maxLayers = maxLayers;
            this.weight = weight;
            this.healthBonus = healthBonus;
            this.damageBonus = damageBonus;
            this.speedBonus = speedBonus;
            this.customName = customName;
            this.customNbt = customNbt;
        }
    }

    public static class SpawnConfig {
        private final List<SpawnRule> rules;
        private final int totalWeight;

        public SpawnConfig(List<SpawnRule> rules) {
            this.rules = rules;
            this.totalWeight = rules.stream().mapToInt(r -> r.weight).sum();
        }

        public SpawnRule getRandomRule(Random random) {
            if (rules.isEmpty() || totalWeight <= 0) return null;
            int randomWeight = random.nextInt(totalWeight);
            int currentWeight = 0;
            for (SpawnRule rule : rules) {
                currentWeight += rule.weight;
                if (randomWeight < currentWeight) {
                    return rule;
                }
            }
            return rules.get(rules.size() - 1);
        }
    }
}