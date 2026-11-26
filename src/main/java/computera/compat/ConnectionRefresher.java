package computera.compat;

import computera.block.Connections;
import computera.block.CordBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Predicate;

/**
 * Saved cord states can be stale: a cable placed while the neighbouring chunk
 * was not loaded keeps "no arm" in its NBT and would render a gap forever. The
 * chunk load event fires before the chunk is promoted to FULL, so the repair is
 * queued and replayed on the next server tick.
 */
public final class ConnectionRefresher {
    private static final Predicate<BlockState> CORD =
            state -> state.getBlock() instanceof CordBlock;
    private static final Deque<Pending> QUEUE = new ArrayDeque<>();
    /** Upper bound so a fast-moving player cannot grow the queue without limit. */
    private static final int MAX_QUEUE = 4096;
    private static final int PER_TICK = 16;

    private record Pending(ServerLevel level, ChunkPos pos) {
    }

    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (!containsCord(event.getChunk())) {
            return;
        }
        if (QUEUE.size() >= MAX_QUEUE) {
            QUEUE.poll();
        }
        QUEUE.add(new Pending(level, event.getChunk().getPos()));
    }

    public static void onLevelUnload(ServerLevel level) {
        QUEUE.removeIf(pending -> pending.level() == level);
    }

    public static void clearAll() {
        QUEUE.clear();
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        int budget = PER_TICK;
        while (budget-- > 0 && !QUEUE.isEmpty()) {
            Pending pending = QUEUE.poll();
            ServerLevel level = event.getServer().getLevel(pending.level().dimension());
            if (level == null) {
                continue;
            }
            @Nullable LevelChunk chunk = level.getChunkSource()
                    .getChunkNow(pending.pos().getMinBlockX() >> 4, pending.pos().getMinBlockZ() >> 4);
            if (chunk == null) {
                continue;
            }
            refreshChunk(level, chunk);
        }
    }

    private static boolean containsCord(LevelChunk chunk) {
        for (LevelChunkSection section : chunk.getSections()) {
            if (section != null && !section.hasOnlyAir() && section.maybeHas(CORD)) {
                return true;
            }
        }
        return false;
    }

    private static void refreshChunk(ServerLevel level, LevelChunk chunk) {
        LevelChunkSection[] sections = chunk.getSections();
        ChunkPos chunkPos = chunk.getPos();
        int baseX = chunkPos.getMinBlockX();
        int baseZ = chunkPos.getMinBlockZ();
        for (int index = 0; index < sections.length; index++) {
            LevelChunkSection section = sections[index];
            if (section == null || section.hasOnlyAir() || !section.maybeHas(CORD)) {
                continue;
            }
            int baseY = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(index));
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        BlockState state = section.getBlockState(x, y, z);
                        if (!(state.getBlock() instanceof CordBlock)) {
                            continue;
                        }
                        BlockPos pos = new BlockPos(baseX + x, baseY + y, baseZ + z);
                        BlockState updated = Connections.withConnections(level, pos, state);
                        if (updated != state) {
                            level.setBlock(pos, updated, CordBlock.UPDATE_CLIENTS | CordBlock.UPDATE_NEIGHBORS);
                        }
                    }
                }
            }
        }
    }

    private ConnectionRefresher() {
    }
}
