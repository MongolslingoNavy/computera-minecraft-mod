package computera.mixin;

import computera.menu.RemoteMenus;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerMixin {
    @Redirect(method = {"handleSetBeaconPacket", "handleContainerClick",
            "handlePlaceRecipe", "handleContainerButtonClick"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/inventory/AbstractContainerMenu;stillValid(Lnet/minecraft/world/entity/player/Player;)Z"))
    private boolean computera$remoteMenuValid(AbstractContainerMenu menu, Player player) {
        return RemoteMenus.stillValid(menu, player);
    }
}
