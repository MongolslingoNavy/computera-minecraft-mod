package computera.cluster;

import computera.blockentity.CabinetBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.LinkedHashSet;
import java.util.Set;

public final class Cluster {
    public static final int MAX_THREADS = 8192;

    private final ServerLevel level;
    private final Set<BlockPos> cabinets = new LinkedHashSet<>();
    private final Set<BlockPos> cables = new LinkedHashSet<>();
    private final Set<BlockPos> terminals = new LinkedHashSet<>();
    private final Set<BlockPos> ports = new LinkedHashSet<>();
    private final Set<BlockPos> pipes = new LinkedHashSet<>();

    Cluster(ServerLevel level) {
        this.level = level;
    }

    void addCabinet(BlockPos pos) {
        cabinets.add(pos.immutable());
    }

    void addCable(BlockPos pos) {
        cables.add(pos.immutable());
    }

    void addTerminal(BlockPos pos) {
        terminals.add(pos.immutable());
    }

    void addPort(BlockPos pos) {
        ports.add(pos.immutable());
    }

    void addPipe(BlockPos pos) {
        pipes.add(pos.immutable());
    }

    public ServerLevel level() {
        return level;
    }

    public int cabinetCount() {
        return cabinets.size();
    }

    public int terminalCount() {
        return terminals.size();
    }

    public int usedThreads() {
        int used = terminals.size();
        for (BlockPos pos : terminals) {
            if (level.getBlockEntity(pos) instanceof computera.blockentity.TerminalBlockEntity terminal
                    && terminal.isRunning()) {
                used++;
            }
        }
        return used;
    }

    public boolean canAdmitTerminal() {
        return terminals.size() <= threadCapacity();
    }

    public Set<BlockPos> cabinets() {
        return cabinets;
    }

    public Set<BlockPos> cables() {
        return cables;
    }

    public Set<BlockPos> terminals() {
        return terminals;
    }

    public Set<BlockPos> ports() {
        return ports;
    }

    public Set<BlockPos> pipes() {
        return pipes;
    }

    public Set<BlockPos> blocks() {
        Set<BlockPos> all = new LinkedHashSet<>(cabinets);
        all.addAll(cables);
        all.addAll(terminals);
        all.addAll(ports);
        all.addAll(pipes);
        return all;
    }

    public int threadCapacity() {
        int total = 0;
        for (BlockPos pos : cabinets) {
            if (level.getBlockEntity(pos) instanceof CabinetBlockEntity cabinet) {
                total += cabinet.installedThreads();
            }
        }
        return Math.min(total, MAX_THREADS);
    }

    public CabinetBlockEntity primaryCabinet() {
        for (BlockPos pos : cabinets) {
            if (level.getBlockEntity(pos) instanceof CabinetBlockEntity cabinet) {
                return cabinet;
            }
        }
        return null;
    }

    public boolean isLoaded() {
        for (BlockPos pos : cabinets) {
            if (!level.isLoaded(pos)) {
                return false;
            }
        }
        return true;
    }

    public void tick() {
        if (!pipes.isEmpty() && level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            computera.logistics.LogisticsMover.move(serverLevel, this);
        }
        int running = 0;
        for (BlockPos pos : terminals) {
            if (level.getBlockEntity(pos) instanceof computera.blockentity.TerminalBlockEntity terminal
                    && terminal.isRunning()) {
                running++;
            }
        }
        int budget = running == 0 ? 0 : computera.blockentity.TerminalBlockEntity.TICK_BUDGET / running;
        for (BlockPos pos : terminals) {
            if (level.getBlockEntity(pos) instanceof computera.blockentity.TerminalBlockEntity terminal) {
                terminal.serverTick();
                terminal.tickScript(budget);
            }
        }
    }
}
