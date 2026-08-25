package net.zombiesleeping.block;

import net.minecraft.core.BlockPos;
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
import net.zombiesleeping.procedures.ConfigProcedure;
import net.minecraft.world.level.pathfinder.PathComputationType;
/**
 * Permanenter Endzustand nach dem Abbrennen (analog zu Burnts eigenem
 * BURNT_WOOL etc.): kein randomTick mehr, keine Partikel, keine
 * Zombie-Spawns - liegt einfach als ausgebrannter, kalter Haufen rum.
 *
 * Property-Name "layers" bewusst identisch gehalten, damit
 * BurntBlockUtil.copyProperties() beim Umwandeln aus dem Smoldering-Block
 * die aktuelle Schichtanzahl automatisch mitnimmt.
 */
public class BurntZombieremainsBlock extends FallingBlock {

    public static final IntegerProperty LAYERS = IntegerProperty.create("layers", 1, 16);
    private static volatile VoxelShape[] shapeCache = null;

    public BurntZombieremainsBlock() {
        super(BlockBehaviour.Properties.of()
                .sound(SoundType.GRAVEL)
                .strength(1f, 10f));
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
	public boolean isPathfindable(BlockState state, BlockGetter world, BlockPos pos, PathComputationType type) {
		// Zwingt die KI dazu, den Block NICHT als Luft zu behandeln, 
		// sondern stattdessen zwingend den BlockPathType abzufragen.
		return false;
	}
    // Bewusst KEIN randomTick, KEIN isFlammable/getFlammability-Override:
    // Endzustand - brennt nicht nochmal, spawnt nichts mehr, tut nichts.
}