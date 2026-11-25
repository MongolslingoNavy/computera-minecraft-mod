package computera.blockentity;

import computera.Computera;
import computera.compat.IndustrialCraftEu;
import computera.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jetbrains.annotations.Nullable;

public class EuAdapterBlockEntity extends BlockEntity {
    private final SimpleEnergyHandler energy = new SimpleEnergyHandler(100_000, 10_000, 10_000);

    private boolean registered;
    @Nullable
    private Object port;

    public EuAdapterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.EU_ADAPTER.get(), pos, state);
    }

    public SimpleEnergyHandler energy() {
        return energy;
    }

    public boolean connected() {
        return registered;
    }

    public void serverTick() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (!registered && IndustrialCraftEu.available()) {
            connect(serverLevel);
        }
        pushPower(serverLevel);
    }

    private void connect(ServerLevel serverLevel) {
        port = IndustrialCraftEu.createPort(worldPosition, this::isRemoved, fe -> {
            int amount = (int) Math.min(Integer.MAX_VALUE, Math.max(0, fe));
            if (amount <= 0) {
                return;
            }
            try (Transaction transaction = Transaction.openRoot()) {
                energy.insert(amount, transaction);
                transaction.commit();
                setChanged();
            }
        });
        registered = IndustrialCraftEu.register(serverLevel, port);
        if (registered) {
            Computera.LOGGER.info("EU adapter connected to IC2 grid at {}", worldPosition.toShortString());
        }
    }

    private void pushPower(ServerLevel serverLevel) {
        if (energy.getAmountAsInt() <= 0) {
            return;
        }
        int budget = Math.min(energy.getAmountAsInt(), 1000);
        java.util.Set<BlockPos> targets = new java.util.LinkedHashSet<>();
        for (net.minecraft.core.Direction direction : net.minecraft.core.Direction.values()) {
            BlockPos neighbor = worldPosition.relative(direction);
            if (!serverLevel.hasChunkAt(neighbor)) {
                continue;
            }
            targets.add(neighbor);
            if (serverLevel.getBlockState(neighbor).is(computera.registry.ModBlocks.CABLE.get())) {
                var cluster = computera.cluster.ClusterManager.clusterFor(serverLevel, neighbor);
                if (cluster != null) {
                    targets.addAll(cluster.cabinets());
                }
            }
        }
        for (BlockPos target : targets) {
            if (budget <= 0) {
                break;
            }
            net.minecraft.core.Direction side = null;
            for (net.minecraft.core.Direction direction : net.minecraft.core.Direction.values()) {
                if (worldPosition.relative(direction).equals(target)) {
                    side = direction.getOpposite();
                    break;
                }
            }
            var handler = serverLevel.getCapability(Capabilities.Energy.BLOCK, target, side);
            if (handler == null) {
                continue;
            }
            try (Transaction transaction = Transaction.openRoot()) {
                int inserted = handler.insert(budget, transaction);
                if (inserted > 0) {
                    if (energy.extract(inserted, transaction) != inserted) {
                        continue;
                    }
                    transaction.commit();
                    setChanged();
                    if (serverLevel.getBlockEntity(target) instanceof CabinetBlockEntity cabinet) {
                        cabinet.setChanged();
                    }
                    budget -= inserted;
                }
            }
        }
    }

    @Override
    protected void saveAdditional(net.minecraft.world.level.storage.ValueOutput output) {
        super.saveAdditional(output);
        energy.serialize(output.child("Energy"));
    }

    @Override
    protected void loadAdditional(net.minecraft.world.level.storage.ValueInput input) {
        super.loadAdditional(input);
        input.child("Energy").ifPresent(energy::deserialize);
    }

    @Override
    public void setRemoved() {
        if (registered && level instanceof ServerLevel serverLevel) {
            IndustrialCraftEu.unregister(serverLevel, port);
        }
        registered = false;
        port = null;
        super.setRemoved();
    }
}
