package net.zombiesleeping.procedures;

import net.minecraft.world.entity.Mob;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.zombiesleeping.block.ZombieremainsBlock;

@Mod.EventBusSubscriber
public class ZombieRemainsPathingProcedure {

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof Mob mob)) return;

        // Frag die Config, ob der Mob durchlaufen darf
        if (ConfigProcedure.isMobImmuneToHazard(mob.getType())) {
            // Darf durch: Kein Malus (0.0F)
            mob.setPathfindingMalus(ZombieremainsBlock.ZOMBIE_REMAINS_HAZARD, 0.0F);
        } else {
            // Darf NICHT durch: Harter Malus (-1.0F reicht jetzt aus, dank unserem Mixin!)
            mob.setPathfindingMalus(ZombieremainsBlock.ZOMBIE_REMAINS_HAZARD, -1.0F);
        }
    }
}