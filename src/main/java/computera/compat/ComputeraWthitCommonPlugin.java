package computera.compat;

import computera.blockentity.CabinetBlockEntity;
import computera.blockentity.CrateBlockEntity;
import computera.blockentity.DisplayPanelBlockEntity;
import computera.blockentity.EuAdapterBlockEntity;
import computera.blockentity.LogisticsPortBlockEntity;
import computera.blockentity.PunchMachineBlockEntity;
import computera.blockentity.TerminalBlockEntity;
import mcp.mobius.waila.api.ICommonRegistrar;
import mcp.mobius.waila.api.IDataProvider;
import mcp.mobius.waila.api.IDataWriter;
import mcp.mobius.waila.api.IPluginConfig;
import mcp.mobius.waila.api.IServerAccessor;
import mcp.mobius.waila.api.IWailaCommonPlugin;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Optional WTHIT common plugin syncing live Computera telemetry, loaded via {@code wthit_plugins.json}.
 */
public final class ComputeraWthitCommonPlugin implements IWailaCommonPlugin {

    @Override
    public void register(ICommonRegistrar registrar) {
        registrar.blockData(DeviceDataProvider.INSTANCE, EuAdapterBlockEntity.class);
        registrar.blockData(DeviceDataProvider.INSTANCE, CabinetBlockEntity.class);
        registrar.blockData(DeviceDataProvider.INSTANCE, LogisticsPortBlockEntity.class);
        registrar.blockData(DeviceDataProvider.INSTANCE, CrateBlockEntity.class);
        registrar.blockData(DeviceDataProvider.INSTANCE, DisplayPanelBlockEntity.class);
        registrar.blockData(DeviceDataProvider.INSTANCE, TerminalBlockEntity.class);
        registrar.blockData(DeviceDataProvider.INSTANCE, PunchMachineBlockEntity.class);
    }

    private enum DeviceDataProvider implements IDataProvider<BlockEntity> {
        INSTANCE;

        @Override
        public void appendData(IDataWriter data, IServerAccessor<BlockEntity> accessor, IPluginConfig config) {
            BlockEntity entity = accessor.getTarget();
            if (entity != null && accessor.getLevel() != null) {
                ComputeraProbeHelper.writeBlockData(data.raw(), accessor.getLevel(), entity);
            }
        }
    }
}
