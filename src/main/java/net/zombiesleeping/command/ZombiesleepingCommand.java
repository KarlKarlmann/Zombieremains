package net.zombiesleeping.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.core.particles.ParticleTypes;

import net.zombiesleeping.ZombiesleepingMod;
import net.zombiesleeping.procedures.ConfigProcedure;

/**
 * Registers server commands for testing modpack configurations.
 * Uses the default Brigadier CommandDispatcher.
 */
@Mod.EventBusSubscriber(modid = ZombiesleepingMod.MODID)
public class ZombiesleepingCommand {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("zombiesleeping")
            .requires(source -> source.hasPermission(2)) // OP level 2 only (Admins/Cheats enabled)
            .then(Commands.literal("spawn")
                .then(Commands.argument("type", StringArgumentType.word())
                    .suggests((context, builder) -> {
                        // Auto-completion for the player
                        builder.suggest("remainsblock");
                        builder.suggest("smolderingremainsblock");
                        return builder.buildFuture();
                    })
                    .then(Commands.argument("layers", IntegerArgumentType.integer(1, 16))
                        .executes(ZombiesleepingCommand::executeSpawn)
                    )
                )
            )
        );
    }

    private static int executeSpawn(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        
        // Check if a real player is executing the command (needed for position)
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendSystemMessage(Component.literal("§cThis command can only be executed by a player."));
            return 0;
        }

        String type = StringArgumentType.getString(context, "type");
        int layers = IntegerArgumentType.getInteger(context, "layers");

        ConfigProcedure.SpawnRule rule = null;
        boolean isSmoldering = false;

        // Query the corresponding config table
        if (type.equalsIgnoreCase("remainsblock")) {
            rule = ConfigProcedure.getRandomSpawnRuleForLayers(layers, new java.util.Random());
        } else if (type.equalsIgnoreCase("smolderingremainsblock")) {
            rule = ConfigProcedure.getRandomSmolderingSpawnRuleForLayers(layers, new java.util.Random());
            isSmoldering = true;
        } else {
            player.sendSystemMessage(Component.literal("§cInvalid block type! Use 'remainsblock' or 'smolderingremainsblock'."));
            return 0;
        }

        if (rule != null && rule.mobType != null) {
            Mob mob = (Mob) rule.mobType.create(player.serverLevel());
            if (mob != null) {
                // Spawn at the exact position of the player
                mob.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 0.0F);
                
                // The magic happens here: NBT & Buffs from the config are applied!
                ConfigProcedure.applyEnhancements(mob, rule, layers);

                // Simulate block behavior
                if (isSmoldering) {
                    mob.setSecondsOnFire(8);
                    player.serverLevel().sendParticles(ParticleTypes.LAVA, 
                        player.getX(), player.getY() + 0.5, player.getZ(), 
                        5, 0.2, 0.2, 0.2, 0.05);
                } else {
                    player.serverLevel().sendParticles(ParticleTypes.POOF, 
                        player.getX(), player.getY() + 0.5, player.getZ(), 
                        5, 0.3, 0.3, 0.3, 0.1);
                }

                player.serverLevel().addFreshEntity(mob);
                
                player.sendSystemMessage(Component.literal("§a✔ Mob successfully generated from the spawn table!"));
                return 1;
            }
        }

        // In case the table is empty or weight wasn't met
        player.sendSystemMessage(Component.literal("§eNo valid mob found in the config for this setup (Type: " + type + ", Layers: " + layers + "). Check your values!"));
        return 0;
    }
}