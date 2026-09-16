package net.zombiesleeping.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.zombiesleeping.init.ZombiesleepingModBlocks;
import net.zombiesleeping.procedures.ConfigProcedure;
import net.minecraft.world.level.pathfinder.PathComputationType;

/**
 * "Ausgebrannte" Variante von ZombieremainsBlock.
 *
 * WICHTIG: Property-Name "layers" (und Wertebereich 1-16) ist bewusst
 * IDENTISCH zu ZombieremainsBlock.LAYERS gehalten. Burnt kopiert beim
 * Umwandeln ueber BurntBlockUtil.copyProperties() Blockstates rein
 * namensbasiert - so bleibt die aktuelle Schichtanzahl beim Umschalten
 * auf diesen Block automatisch erhalten, ohne dass wir mit Burnt reden
 * muessen.
 *
 * Keine Zombie-Spawns, keine Greifhaende mehr - der Haufen brennt und
 * verglueht ueber Zeit selbststaendig per randomTick, unabhaengig davon
 * ob Burnt ueberhaupt installiert ist.
 */
public class SmolderingZombieremainsBlock extends FallingBlock {

    public static final IntegerProperty LAYERS = IntegerProperty.create("layers", 1, 16);
    private static volatile VoxelShape[] shapeCache = null;

    public SmolderingZombieremainsBlock() {
        super(BlockBehaviour.Properties.of()
                .sound(SoundType.GRAVEL)
                .strength(1f, 10f)
                .lightLevel(state -> 8)
                .randomTicks());
        this.registerDefaultState(this.stateDefinition.any().setValue(LAYERS, 1));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LAYERS);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
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
    public void randomTick(BlockState state, ServerLevel world, BlockPos pos, RandomSource random) {
        super.randomTick(state, world, pos, random);

        int layers = state.getValue(LAYERS);

        // Rauch-Ambiente waehrend der gesamten Smoldering-Phase
        world.sendParticles(ParticleTypes.SMOKE,
                pos.getX() + 0.5, pos.getY() + 0.15 + (layers / 32.0), pos.getZ() + 0.5,
                2, 0.25, 0.05, 0.25, 0.01);

        trySpreadFire(world, pos, random);

        // RESOLVE_CHANCE aus der Config laden (Ist der Block "fertig" mit schwelen?)
        if (random.nextFloat() >= ConfigProcedure.SMOLDERING_RESOLVE_CHANCE.get()) {
            return; 
        }

        // CHANCE_TO_COOL_DOWN aus der Config laden (Kühlt er ab oder wird er zerstört?)
        if (random.nextFloat() < ConfigProcedure.SMOLDERING_CHANCE_TO_COOL_DOWN.get()) {
            // Endzustand: kuehlt zu ausgebrannten, permanenten Resten ab
            BlockState burnt = ZombiesleepingModBlocks.BURNT_ZOMBIEREMAINS.get()
                    .defaultBlockState()
                    .setValue(BurntZombieremainsBlock.LAYERS, layers);
            world.setBlock(pos, burnt, 3);

            // Spawn-Versuch mit der speziellen Smoldering-Tabelle
            if (random.nextFloat() < ConfigProcedure.SMOLDERING_SPAWN_CHANCE.get()) { 
                ConfigProcedure.SpawnRule rule = ConfigProcedure.getRandomSmolderingSpawnRuleForLayers(layers, new java.util.Random(world.random.nextLong()));
                
                if (rule != null && rule.mobType != null) {
                    Mob mob = (Mob) rule.mobType.create(world);
                    if (mob != null) {
                        mob.moveTo(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, random.nextFloat() * 360, 0.0F);
                        
                        // Hier passiert die Magie: HP, Damage, Speed, Name und Custom-Texture-NBT 
                        // werden zentral und sauber auf das Entity angewendet!
                        ConfigProcedure.applyEnhancements(mob, rule, layers);
                        
                        // Brennender Aschezombie für mehr Atmosphäre
                        mob.setSecondsOnFire(8);

                        world.addFreshEntity(mob);
                        
                        // Coole Partikel für den "Asche-Spawn"
                        world.sendParticles(ParticleTypes.LAVA, 
                            pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 
                            5, 0.2, 0.2, 0.2, 0.05);
                    }
                }
            }
        } else {
            // Alternative: verglueht komplett, nichts bleibt uebrig
            world.removeBlock(pos, false);
            world.sendParticles(ParticleTypes.LARGE_SMOKE,
                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    8, 0.3, 0.2, 0.3, 0.02);
        }

        // Zischen beim Erlöschen abspielen
        world.playSound(null, pos, net.minecraft.sounds.SoundEvents.FIRE_EXTINGUISH,
                net.minecraft.sounds.SoundSource.BLOCKS, 0.5F, 1.2F);
    }

    /**
     * Setzt mit kleiner Chance ganz normales Vanilla-Feuer in eine freie Nachbarzelle. 
     * Bewusst KEIN direkter Aufruf von Burnts eigenen Prozeduren (WillitburnProcedure etc.), 
     * um ohne harte Abhaengigkeit auszukommen.
     */
    private void trySpreadFire(ServerLevel world, BlockPos pos, RandomSource random) {
        // FIRE_SPREAD_CHANCE jetzt aus der Config geladen
        if (random.nextFloat() >= ConfigProcedure.SMOLDERING_FIRE_SPREAD_CHANCE.get()) {
            return;
        }
        Direction dir = Direction.values()[random.nextInt(Direction.values().length)];
        BlockPos target = pos.relative(dir);
        BlockState targetState = world.getBlockState(target);
        if (!targetState.isAir()) {
            return;
        }
        BlockState fireState = net.minecraft.world.level.block.Blocks.FIRE.defaultBlockState();
        if (fireState.canSurvive(world, target)) {
            world.setBlock(target, fireState, 11);
        }
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
    public boolean isPathfindable(BlockState state, BlockGetter world, BlockPos pos, PathComputationType type) {
        // Erlaubt der KI, einen Weg auf dem Landweg (LAND) durch diesen Block zu planen
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
}