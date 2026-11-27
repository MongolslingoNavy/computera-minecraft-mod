package computera.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import computera.menu.RemoteMenus;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {
    @WrapOperation(method = {"tick", "doTick"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/inventory/AbstractContainerMenu;stillValid(Lnet/minecraft/world/entity/player/Player;)Z"))
    private boolean computera$remoteMenuValid(AbstractContainerMenu menu, Player player, Operation<Boolean> original) {
        return RemoteMenus.stillValid(menu, player, original);
    }
}
