package net.zombiesleeping.block;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.RandomSource;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.chat.Component;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.phys.shapes.Shapes;
import net.zombiesleeping.procedures.ConfigProcedure;
import net.zombiesleeping.init.ZombiesleepingModBlocks;
import net.minecraft.world.level.block.FallingBlock;
import java.util.List;
import java.util.ArrayList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.Level;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.core.Direction;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.entity.monster.Enemy;
import javax.annotation.Nullable;
import net.minecraft.world.level.pathfinder.PathComputationType;

public class ZombieremainsBlock extends FallingBlock {

    public static final IntegerProperty LAYERS = IntegerProperty.create("layers", 1, 16);
    private static volatile VoxelShape[] shapeCache = null;
    public static final BlockPathTypes ZOMBIE_REMAINS_HAZARD =
        BlockPathTypes.create("zombiesleeping_remains", -1.0F); // -1.0F = Default: meiden, wie DAMAGE_OTHER

    public ZombieremainsBlock() {
        super(BlockBehaviour.Properties.of()
                .sound(SoundType.GRAVEL)
                .strength(1f, 10f)
                .randomTicks()
                .isValidSpawn((state, level, pos, entityType) -> false)
        );
        this.registerDefaultState(this.stateDefinition.any().setValue(LAYERS, 1));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LAYERS);
    }

    @Override
    public boolean isPathfindable(BlockState state, BlockGetter world, BlockPos pos, PathComputationType type) {
        switch (type) {
            case LAND:
                return true;
            case WATER:
                return false;
            case AIR:
                return false;
            default:
                return false;
        }
    }

    @Override
    public BlockPathTypes getBlockPathType(BlockState state, BlockGetter level, BlockPos pos, @Nullable Mob mob) {
        return ZOMBIE_REMAINS_HAZARD;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    public boolean isFlammable(BlockState state, BlockGetter world, BlockPos pos, Direction face) {
        return true;
    }

    @Override
    public int getFlammability(BlockState state, BlockGetter world, BlockPos pos, Direction face) {
        return 60; 
    }

    @Override
    public int getFireSpreadSpeed(BlockState state, BlockGetter world, BlockPos pos, Direction face) {
        return 30; 
    }

    @Override
    public void onCaughtFire(BlockState state, Level world, BlockPos pos, @Nullable Direction face, @Nullable LivingEntity igniter) {
        if (!world.isClientSide()) {
            int layers = state.getValue(LAYERS);
            BlockState smoldering = ZombiesleepingModBlocks.SMOLDERING_ZOMBIEREMAINS.get()
                    .defaultBlockState()
                    .setValue(SmolderingZombieremainsBlock.LAYERS, layers);
            world.setBlock(pos, smoldering, 3);
        }
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, net.minecraft.world.level.LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        boolean isAnyFire = neighborState.is(net.minecraft.world.level.block.Blocks.FIRE) 
            || neighborState.getBlock() instanceof net.minecraft.world.level.block.BaseFireBlock
            || neighborState.getBlock().getClass().getName().contains("burnt");

        if (!level.isClientSide() && isAnyFire) {
            if (level instanceof ServerLevel serverLevel) {
                int layers = state.getValue(LAYERS);
                BlockState smoldering = ZombiesleepingModBlocks.SMOLDERING_ZOMBIEREMAINS.get()
                        .defaultBlockState()
                        .setValue(SmolderingZombieremainsBlock.LAYERS, layers);

                serverLevel.getServer().execute(() -> {
                    if (serverLevel.getBlockState(pos).getBlock() instanceof ZombieremainsBlock) {
                        serverLevel.setBlock(pos, smoldering, 3);
                    }
                });
            }
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override 
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) { 
        return getShapeForLayers(state.getValue(LAYERS)); 
    }
     
    private VoxelShape getShapeForLayers(int layers) { 
        if (shapeCache == null || shapeCache.length != ConfigProcedure.MAX_LAYERS.get() + 1) { 
            initShapeCache(); 
        } 
        return shapeCache[Math.min(layers, shapeCache.length - 1)]; 
    } 

    private void initShapeCache() { 
        int maxLayers = ConfigProcedure.MAX_LAYERS.get(); 
        shapeCache = new VoxelShape[maxLayers + 1]; 
        shapeCache[0] = Block.box(0.0, 0.0, 0.0, 16.0, 0.0, 16.0); 

        for (int i = 1; i <= maxLayers; i++) { 
            double height = Math.min(16.0, (16.0 / maxLayers) * i); 
            shapeCache[i] = Block.box(0.0, 0.0, 0.0, 16.0, height, 16.0); 
        } 
    }
    
    @Override
    public boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
        return false;
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader world, BlockPos pos) {
        return super.canSurvive(state, world, pos);
    }  

    @Override
    public void onLand(Level world, BlockPos pos, BlockState fallingState, BlockState hitState, FallingBlockEntity fallingBlock) {
        super.onLand(world, pos, fallingState, hitState, fallingBlock);
        
        if (!world.isClientSide && world instanceof ServerLevel serverLevel) {
            if (serverLevel.getRandom().nextFloat() < 0.15f) {
                spawnMobsFromBlock(serverLevel, pos, fallingState, null, false);
            }
        }
    }

    @Override
    public void entityInside(BlockState state, Level world, BlockPos pos, Entity entity) {
        if (entity instanceof LivingEntity livingEntity && livingEntity.getMobType() != MobType.UNDEAD) {

            if (!world.isClientSide && world instanceof ServerLevel serverLevel) {

                if (entity.tickCount % 10 == 0) {
                    int layers = state.getValue(LAYERS);

                    int chance = 5 + (layers * 3);
                    
                    if (serverLevel.getRandom().nextInt(100) < chance) {
                        
                        float oldHealth = livingEntity.getHealth();
                        float baseDamage = 1.0f + (layers * 0.25f); 

                        boolean wasHurt = livingEntity.hurt(serverLevel.damageSources().generic(), baseDamage);
                        
                        if (wasHurt) {
                            float actualDamageTaken = oldHealth - livingEntity.getHealth();

                            if (actualDamageTaken > 0.5f) {
                                for (ConfigProcedure.ConfiguredEffect debuff : ConfigProcedure.appliedDebuffs) {
                                    livingEntity.addEffect(new MobEffectInstance(
                                        debuff.effect, 
                                        debuff.duration, 
                                        debuff.amplifier, 
                                        false, 
                                        true
                                    ));
                                }

                                world.playSound(null, pos, SoundEvents.ZOMBIE_STEP, SoundSource.BLOCKS, 1.0F, 0.5F);
                                world.playSound(null, pos, SoundEvents.ZOMBIE_INFECT, SoundSource.BLOCKS, 0.2F, 0.6F);
                                
                                serverLevel.sendParticles((net.minecraft.core.particles.SimpleParticleType) net.zombiesleeping.init.ZombiesleepingModParticles.ZOMBIE_HAND.get(), 
                                    entity.getX(), pos.getY() + 0.5D, entity.getZ(), 
                                    5, 0.3, 0.2, 0.3, 0.05);
                                    
                                serverLevel.sendParticles(ParticleTypes.DAMAGE_INDICATOR, 
                                    entity.getX(), entity.getY() + 0.5D, entity.getZ(), 
                                    3, 0.2, 0.1, 0.2, 0.01);
                            }
                        }
                    }
                }
            }
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        List<ItemStack> drops = new ArrayList<>();
        int layers = state.getValue(LAYERS);
        
        ResourceLocation lootTableId = this.getLootTable();
        
        if (lootTableId != null) {
            LootParams lootParams = builder.withParameter(LootContextParams.BLOCK_STATE, state)
                                         .create(LootContextParamSets.BLOCK);
            
            LootTable lootTable = lootParams.getLevel().getServer().getLootData().getLootTable(lootTableId);
            
            for (int i = 0; i < layers; i++) {
                drops.addAll(lootTable.getRandomItems(lootParams));
            }
        }
        
        return drops;
    }

    @Override
    public int getLightBlock(BlockState state, BlockGetter worldIn, BlockPos pos) {
        int layers = state.getValue(LAYERS);
        int maxLayers = ConfigProcedure.MAX_LAYERS.get();
        return Math.min(15, (layers * 15) / maxLayers);
    }

    @Override
    public void randomTick(BlockState state, ServerLevel world, BlockPos pos, RandomSource random) {
        super.randomTick(state, world, pos, random);

        int detectionRadius = ConfigProcedure.PLAYER_DETECTION_RADIUS.get();

        if (!world.hasNearbyAlivePlayer(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, detectionRadius)) {
            return;
        }

        int lightLevel = world.getBrightness(LightLayer.BLOCK, pos.above());
        if (lightLevel > ConfigProcedure.MAX_LIGHT_LEVEL.get()) return;

        if (random.nextFloat() < ConfigProcedure.SPAWN_CHANCE.get()) {
            net.zombiesleeping.ZombieSpawnPipeline.enqueue(world, pos.immutable());
        }
    }

    public static int spawnMobsFromBlock(ServerLevel world, BlockPos pos,BlockState state, Player targetPlayer, boolean forceSpawnAll) {
        if (!(state.getBlock() instanceof ZombieremainsBlock)) {
            return 0;
        }

        int currentLayers = state.getValue(LAYERS);
        int mobsSpawned = 0;
        RandomSource random = world.getRandom();
        
        int layersToProcess = forceSpawnAll ? currentLayers : 1;
        
        for (int i = 0; i < layersToProcess; i++) {
            EntityType<?> mobType = ConfigProcedure.getRandomMobForLayers(currentLayers - i, new java.util.Random(world.random.nextLong()));
            if (mobType != null) {
                Mob mob = (Mob) mobType.create(world);
                if (mob != null) {
                    double spawnX = pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 1.5;
                    double spawnY = pos.getY() + 1;
                    double spawnZ = pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 1.5;

                    mob.moveTo(spawnX, spawnY, spawnZ, random.nextFloat() * 360, 0.0F);

                    enhanceMobFromConfig(mob, currentLayers - i);

                    if (targetPlayer != null) {
                        mob.setTarget(targetPlayer);
                    }

                    world.addFreshEntity(mob);
                    mobsSpawned++;
                    world.sendParticles(ParticleTypes.POOF, spawnX, spawnY + 0.5, spawnZ, 
                        5, 0.3, 0.3, 0.3, 0.1);
                }
            }
        }

        if (forceSpawnAll || currentLayers <= 1) {
            world.removeBlock(pos, false);
        } else {
            world.setBlock(pos, state.setValue(LAYERS, currentLayers - 1), 3);
        }
        
        return mobsSpawned;
    }

    private static void enhanceMobFromConfig(Mob mob, int currentLayers) {
        ConfigProcedure.MobEnhancement enhancement = 
            ConfigProcedure.getEnhancementForMob(mob.getType(), currentLayers);
            
        if (enhancement == null) return;

        int layerMultiplier = currentLayers - enhancement.minLayers + 1;

        if (enhancement.healthBonus > 0) {
            float bonusHealth = enhancement.healthBonus * layerMultiplier;
            if (mob.getAttribute(Attributes.MAX_HEALTH) != null) {
                double newMaxHealth = mob.getAttribute(Attributes.MAX_HEALTH).getBaseValue() + bonusHealth;
                mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(newMaxHealth);
                mob.setHealth((float) newMaxHealth);
            }
        }

        if (enhancement.damageBonus > 0 && mob.getAttribute(Attributes.ATTACK_DAMAGE) != null) {
            float bonusDamage = enhancement.damageBonus * layerMultiplier;
            double newDamage = mob.getAttribute(Attributes.ATTACK_DAMAGE).getBaseValue() + bonusDamage;
            mob.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(newDamage);
        }

        if (enhancement.speedBonus > 0 && mob.getAttribute(Attributes.MOVEMENT_SPEED) != null) {
            float bonusSpeed = enhancement.speedBonus * layerMultiplier;
            double newSpeed = mob.getAttribute(Attributes.MOVEMENT_SPEED).getBaseValue() + bonusSpeed;
            mob.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(newSpeed);
        }

        if (!enhancement.customName.isEmpty()) {
            String colorCode = currentLayers >= 7 ? "§4" : currentLayers >= 5 ? "§6" : "§a";
            mob.setCustomName(Component.literal(colorCode + enhancement.customName));
            mob.setCustomNameVisible(true);
        }
    }

    public static boolean addLayer(ServerLevel world, BlockPos pos, int layersToAdd) {
        BlockState state = world.getBlockState(pos);
        
        if (state.getBlock() instanceof ZombieremainsBlock) {
            int currentLayers = state.getValue(LAYERS);
            int maxLayers = ConfigProcedure.MAX_LAYERS.get();
            int newLayers = Math.min(maxLayers, currentLayers + layersToAdd);
            
            if (newLayers != currentLayers) {
                world.setBlock(pos, state.setValue(LAYERS, newLayers), 3);
                return true;
            }
        }
        return false;
    }

    public static boolean addLayer(ServerLevel world, BlockPos pos) {
        return addLayer(world, pos, 1);
    }
}