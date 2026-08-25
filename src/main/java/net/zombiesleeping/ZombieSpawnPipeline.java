package net.zombiesleeping;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.zombiesleeping.block.ZombieremainsBlock;
import net.zombiesleeping.procedures.ConfigProcedure;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Entkoppelt das Spawnen von Mobs aus Zombieremains-Bloecken vom randomTick
 * UND vom Screamer-Schrei (ScreamerEntity#convertZombieRemainsInRange).
 *
 * Statt sofort beim Ausloeser zu spawnen (was bei vielen gleichzeitig
 * "ausloesenden" Bloecken oder einem Schrei in einem dicht bebauten
 * Remains-Feld zu einer Lastspitze in einem einzigen Tick fuehren wuerde),
 * werden Spawn-Wuensche hier gesammelt und ueber mehrere Server-Ticks
 * abgearbeitet:
 *
 *   - maximal N Bloecke pro Tick werden verarbeitet (konfigurierbar)
 *   - nur solange der Server noch Luft hat (siehe ZombiesleepingMod#hasSpawnBudget)
 *   - pro Block-Position nur ein Eintrag gleichzeitig in der Queue
 *     (ein bereits wartender Eintrag wird bei Bedarf auf "alle Layer
 *     umwandeln" hochgestuft, statt einen zweiten Eintrag zu erzeugen)
 *
 * Laeuft ausschliesslich auf dem Server-Tick-Thread (randomTick, der Screamer-
 * Tick und der ServerTickEvent-Handler laufen alle dort), daher reichen
 * einfache, nicht-synchronisierte Collections voellig aus.
 */
public final class ZombieSpawnPipeline {

    private static final class Request {
        final ServerLevel level;
        final BlockPos pos;
        boolean forceSpawnAll;
        UUID targetPlayerUuid;

        Request(ServerLevel level, BlockPos pos, boolean forceSpawnAll, UUID targetPlayerUuid) {
            this.level = level;
            this.pos = pos;
            this.forceSpawnAll = forceSpawnAll;
            this.targetPlayerUuid = targetPlayerUuid;
        }
    }

    // LinkedHashMap haelt die Einfuege-Reihenfolge (verhaelt sich wie eine FIFO-Queue),
    // erlaubt aber gleichzeitig O(1) Dedupe/Update pro Block-Position.
    private static final Map<BlockPos, Request> queue = new LinkedHashMap<>();

    // Schutz gegen unbegrenztes Wachstum, falls der Server laenger klemmt
    private static final int MAX_QUEUE_SIZE = 200;

    private ZombieSpawnPipeline() {
    }

    /**
     * Reiht einen normalen Spawn-Wunsch ein (ein Layer, kein festes Ziel).
     * Wird von ZombieremainsBlock#randomTick aufgerufen. Spawnt NICHT direkt.
     */
    public static void enqueue(ServerLevel level, BlockPos pos) {
        enqueue(level, pos, null, false);
    }

    /**
     * Reiht einen Spawn-Wunsch ein.
     *
     * @param targetPlayer  optionales Ziel (z.B. vom Screamer uebergeben), darf null sein
     * @param forceSpawnAll true = alle Layer auf einmal umwandeln (Screamer-Schrei),
     *                      false = nur die oberste Schicht (normaler randomTick)
     */
    public static void enqueue(ServerLevel level, BlockPos pos, Player targetPlayer, boolean forceSpawnAll) {
        UUID targetUuid = targetPlayer != null ? targetPlayer.getUUID() : null;

        Request existing = queue.get(pos);
        if (existing != null) {
            // Block wartet schon: bei Bedarf auf "alle Layer" hochstufen,
            // damit ein Schrei nicht einfach durch einen bereits wartenden
            // randomTick-Eintrag verschluckt wird.
            if (forceSpawnAll && !existing.forceSpawnAll) {
                existing.forceSpawnAll = true;
                existing.targetPlayerUuid = targetUuid;
            }
            return;
        }

        if (queue.size() >= MAX_QUEUE_SIZE) {
            return; // Server haengt eh schon genug in der Warteschlange
        }

        queue.put(pos, new Request(level, pos, forceSpawnAll, targetUuid));
    }

    /**
     * Wird einmal pro Server-Tick (Phase.END) aus ZombiesleepingMod aufgerufen.
     * Arbeitet die Queue ab, bis entweder das Pro-Tick-Limit erreicht ist
     * oder der Server keine Kapazitaet mehr hat.
     */
    public static void tick() {
        if (queue.isEmpty()) {
            return;
        }

        int maxPerTick = ConfigProcedure.MAX_SPAWNS_PER_TICK.get();
        int processed = 0;

        var iterator = queue.entrySet().iterator();
        while (iterator.hasNext() && processed < maxPerTick) {
            if (!ZombiesleepingMod.hasSpawnBudget()) {
                break; // Server steht unter Last -> diesen Tick abbrechen, Rest bleibt in der Queue
            }

            Request request = iterator.next().getValue();
            iterator.remove();

            ServerLevel level = request.level;
            BlockPos pos = request.pos;

            if (!level.isLoaded(pos)) {
                continue; // Chunk zwischenzeitlich entladen
            }

            BlockState currentState = level.getBlockState(pos);
            if (!(currentState.getBlock() instanceof ZombieremainsBlock)) {
                continue; // Block existiert nicht mehr / wurde veraendert
            }

            Player targetPlayer = request.targetPlayerUuid != null
                ? level.getPlayerByUUID(request.targetPlayerUuid)
                : null;

            ZombieremainsBlock.spawnMobsFromBlock(level, pos, currentState, targetPlayer, request.forceSpawnAll);
            processed++;
        }
    }
}