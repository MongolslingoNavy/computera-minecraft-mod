package computera.compat;

import computera.Computera;
import computera.block.CabinetBlock;
import computera.block.CrateBlock;
import computera.block.DisplayPanelBlock;
import computera.block.EuAdapterBlock;
import computera.block.LogisticsPortBlock;
import computera.block.PunchMachineBlock;
import computera.block.TerminalBlock;
import computera.blockentity.CabinetBlockEntity;
import computera.blockentity.CrateBlockEntity;
import computera.blockentity.DisplayPanelBlockEntity;
import computera.blockentity.EuAdapterBlockEntity;
import computera.blockentity.LogisticsPortBlockEntity;
import computera.blockentity.PunchMachineBlockEntity;
import computera.blockentity.TerminalBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;

/**
 * Optional Jade plugin syncing and rendering live Computera hardware telemetry.
 * Loaded exclusively by Jade when Jade is installed.
 */
@WailaPlugin
public final class ComputeraJadePlugin implements IWailaPlugin {

    private static final Identifier DEVICE_UID = Identifier.fromNamespaceAndPath(Computera.MODID, "device");

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(DeviceServerProvider.INSTANCE, EuAdapterBlockEntity.class);
        registration.registerBlockDataProvider(DeviceServerProvider.INSTANCE, CabinetBlockEntity.class);
        registration.registerBlockDataProvider(DeviceServerProvider.INSTANCE, LogisticsPortBlockEntity.class);
        registration.registerBlockDataProvider(DeviceServerProvider.INSTANCE, CrateBlockEntity.class);
        registration.registerBlockDataProvider(DeviceServerProvider.INSTANCE, DisplayPanelBlockEntity.class);
        registration.registerBlockDataProvider(DeviceServerProvider.INSTANCE, TerminalBlockEntity.class);
        registration.registerBlockDataProvider(DeviceServerProvider.INSTANCE, PunchMachineBlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(DeviceClientProvider.INSTANCE, EuAdapterBlock.class);
        registration.registerBlockComponent(DeviceClientProvider.INSTANCE, CabinetBlock.class);
        registration.registerBlockComponent(DeviceClientProvider.INSTANCE, LogisticsPortBlock.class);
        registration.registerBlockComponent(DeviceClientProvider.INSTANCE, CrateBlock.class);
        registration.registerBlockComponent(DeviceClientProvider.INSTANCE, DisplayPanelBlock.class);
        registration.registerBlockComponent(DeviceClientProvider.INSTANCE, TerminalBlock.class);
        registration.registerBlockComponent(DeviceClientProvider.INSTANCE, PunchMachineBlock.class);
    }

    private enum DeviceServerProvider implements IServerDataProvider<BlockAccessor> {
        INSTANCE;

        @Override
        public Identifier getUid() {
            return DEVICE_UID;
        }

        @Override
        public void appendServerData(CompoundTag data, BlockAccessor accessor) {
            if (accessor.getLevel() instanceof ServerLevel serverLevel) {
                ComputeraProbeHelper.writeBlockData(data, serverLevel, accessor.getBlockEntity());
            }
        }
    }

    private enum DeviceClientProvider implements IBlockComponentProvider {
        INSTANCE;

        @Override
        public Identifier getUid() {
            return DEVICE_UID;
        }

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            for (Component line : ComputeraProbeHelper.readBlockLines(accessor.getServerData())) {
                tooltip.add(line);
            }
        }
    }
}
