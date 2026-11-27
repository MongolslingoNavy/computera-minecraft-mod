package computera.compat;

import computera.block.CabinetBlock;
import computera.block.CrateBlock;
import computera.block.DisplayPanelBlock;
import computera.block.EuAdapterBlock;
import computera.block.LogisticsPortBlock;
import computera.block.PunchMachineBlock;
import computera.block.TerminalBlock;
import mcp.mobius.waila.api.IBlockAccessor;
import mcp.mobius.waila.api.IBlockComponentProvider;
import mcp.mobius.waila.api.IClientRegistrar;
import mcp.mobius.waila.api.IPluginConfig;
import mcp.mobius.waila.api.ITooltip;
import mcp.mobius.waila.api.IWailaClientPlugin;
import net.minecraft.network.chat.Component;

/**
 * Optional WTHIT client plugin rendering live Computera telemetry, loaded via {@code wthit_plugins.json}.
 */
public final class ComputeraWthitClientPlugin implements IWailaClientPlugin {

    @Override
    public void register(IClientRegistrar registrar) {
        registrar.body(DeviceComponentProvider.INSTANCE, EuAdapterBlock.class);
        registrar.body(DeviceComponentProvider.INSTANCE, CabinetBlock.class);
        registrar.body(DeviceComponentProvider.INSTANCE, LogisticsPortBlock.class);
        registrar.body(DeviceComponentProvider.INSTANCE, CrateBlock.class);
        registrar.body(DeviceComponentProvider.INSTANCE, DisplayPanelBlock.class);
        registrar.body(DeviceComponentProvider.INSTANCE, TerminalBlock.class);
        registrar.body(DeviceComponentProvider.INSTANCE, PunchMachineBlock.class);
    }

    private enum DeviceComponentProvider implements IBlockComponentProvider {
        INSTANCE;

        @Override
        public void appendBody(ITooltip tooltip, IBlockAccessor accessor, IPluginConfig config) {
            for (Component line : ComputeraProbeHelper.readBlockLines(accessor.getData().raw())) {
                tooltip.addLine(line);
            }
        }
    }
}
