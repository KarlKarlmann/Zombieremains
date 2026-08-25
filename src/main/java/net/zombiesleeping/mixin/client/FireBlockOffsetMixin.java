package net.zombiesleeping.mixin.client;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.zombiesleeping.block.ZombieremainsBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockBehaviour.BlockStateBase.class)
public class FireBlockOffsetMixin {

    @Inject(method = "getOffset", at = @At("HEAD"), cancellable = true)
    private void zombiesleeping$lowerFireRenderOffset(BlockGetter level, BlockPos pos, CallbackInfoReturnable<Vec3> cir) {
        BlockState state = (BlockState) (Object) this;
        
		boolean isFireBlock = state.is(Blocks.FIRE) 
			|| state.getBlock() instanceof net.minecraft.world.level.block.BaseFireBlock
			|| state.getBlock().getClass().getName().contains("burnt");

		if (isFireBlock) {
			BlockState stateBelow = level.getBlockState(pos.below());
			
			if (stateBelow.hasProperty(ZombieremainsBlock.LAYERS)) {
				int layers = stateBelow.getValue(ZombieremainsBlock.LAYERS);
				int baseHeightPixels = Math.min(layers, 3);
				double yOffset = -(1.0 - (baseHeightPixels / 16.0));
				
				cir.setReturnValue(new Vec3(0, yOffset, 0));
			}
		}
    }
}