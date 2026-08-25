package net.zombiesleeping.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.zombiesleeping.block.ZombieremainsBlock;
import net.zombiesleeping.block.SmolderingZombieremainsBlock;
import net.zombiesleeping.block.BurntZombieremainsBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WalkNodeEvaluator.class)
public class WalkNodeEvaluatorMixin {

    @Inject(method = "checkNeighbourBlocks", at = @At("HEAD"), cancellable = true)
    private static void zombiesleeping$applyRemainsAura(BlockGetter level, BlockPos.MutableBlockPos pos, BlockPathTypes currentType, CallbackInfoReturnable<BlockPathTypes> cir) {
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        // Eigene MutableBlockPos, um den originalen Parameter nicht zu verfälschen
        BlockPos.MutableBlockPos checkPos = new BlockPos.MutableBlockPos();

        // 3x3x3 Scan um den aktuellen Weg-Knoten (exakt wie Vanilla)
        for (int i = -1; i <= 1; ++i) {
            for (int j = -1; j <= 1; ++j) {
                for (int k = -1; k <= 1; ++k) {
                    if (i == 0 && k == 0) continue;
                    
                    checkPos.set(x + i, y + j, z + k);
                    Block block = level.getBlockState(checkPos).getBlock();
                    
                    // Wenn ein angrenzender Block einer deiner Haufen ist:
                    if (block instanceof ZombieremainsBlock || 
                        block instanceof SmolderingZombieremainsBlock || 
                        block instanceof BurntZombieremainsBlock) {
                        
                        // Strahl unseren eigenen Custom-Typ als Aura auf die Nachbarblöcke aus!
                        cir.setReturnValue(ZombieremainsBlock.ZOMBIE_REMAINS_HAZARD);
                        return;
                    }
                }
            }
        }
    }
}