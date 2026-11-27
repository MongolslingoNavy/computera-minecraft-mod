package computera.util;

import computera.Computera;
import computera.block.DisplayPanelBlock;
import computera.blockentity.CabinetBlockEntity;
import computera.blockentity.DisplayMode;
import computera.blockentity.DisplayPanelBlockEntity;
import computera.blockentity.EuAdapterBlockEntity;
import computera.blockentity.LogisticsPortBlockEntity;
import computera.blockentity.TerminalBlockEntity;
import computera.cluster.ClusterManager;
import computera.registry.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.TriState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Standard {@code c:tools/wrench} (with {@code c:wrenches} fallback) interaction support
 * for all Computera blocks:
 * <ul>
 *     <li>Never lets block interactions hijack a wrench (e.g. setting a wrench as a port filter or opening a GUI).</li>
 *     <li>Right-clicking rotatable blocks (such as {@link DisplayPanelBlock}) rotates to the next valid orientation.</li>
 *     <li>Sneak-right-clicking any Computera block safely dismantles it, preserves machine configuration/energy where
 *     appropriate, drops the block item, and applies 1 point of durability wear to damageable third-party wrenches.</li>
 * </ul>
 */
public final class WrenchSupport {

    private WrenchSupport() {
    }

    public static boolean isWrench(ItemStack stack) {
        return !stack.isEmpty()
                && (stack.is(ModTags.Items.C_TOOLS_WRENCH) || stack.is(ModTags.Items.C_WRENCHES));
    }

    public static boolean isComputeraBlock(Block block) {
        return Computera.MODID.equals(BuiltInRegistries.BLOCK.getKey(block).getNamespace());
    }

    /**
     * Ensures sneak-right-click with any third-party wrench reaches {@link Block#useItemOn}
     * even if that wrench does not override {@code doesSneakBypassUse} or uses {@code onItemUseFirst}.
     */
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        if (player == null || player.isSpectator() || !player.isShiftKeyDown()) {
            return;
        }
        if (!isWrench(event.getItemStack())) {
            return;
        }
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        if (!isComputeraBlock(level.getBlockState(pos).getBlock())) {
            return;
        }
        event.setUseBlock(TriState.TRUE);
        event.setUseItem(TriState.FALSE);
    }

    /**
     * Handles a wrench right-click from {@code Block.useItemOn}. Callers must check {@link #isWrench(ItemStack)}
     * first so non-wrench items keep their existing behavior.
     */
    public static InteractionResult handleBlockUse(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                                   Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (player.isShiftKeyDown()) {
            if (!level.mayInteract(player, pos)) {
                return InteractionResult.FAIL;
            }
            if (level instanceof ServerLevel serverLevel) {
                dismantle(serverLevel, pos, state, player, hand, stack);
            }
            return InteractionResult.SUCCESS;
        }

        if (state.getBlock() instanceof DisplayPanelBlock) {
            BlockState rotated = nextValidDisplayRotation(state, level, pos);
            if (rotated != null) {
                if (!level.mayInteract(player, pos)) {
                    return InteractionResult.FAIL;
                }
                if (level instanceof ServerLevel serverLevel) {
                    serverLevel.setBlock(pos, rotated, Block.UPDATE_ALL);
                    DisplayPanelBlockEntity.refreshAround(serverLevel, pos);
                    ClusterManager.invalidate(serverLevel, pos);
                    var sound = rotated.getSoundType(serverLevel, pos, player);
                    serverLevel.playSound(null, pos, sound.getPlaceSound(), SoundSource.BLOCKS,
                            (sound.getVolume() + 1.0f) / 2.0f, sound.getPitch() * 0.9f);
                    damageWrench(stack, player, hand);
                }
                return InteractionResult.SUCCESS;
            }
        }

        // Do not hijack wrench clicks (no filter assignment, no GUI opening, no error swallowing).
        return InteractionResult.PASS;
    }

    private static BlockState nextValidDisplayRotation(BlockState state, Level level, BlockPos pos) {
        BlockState candidate = state;
        for (int i = 0; i < 3; i++) {
            candidate = candidate.rotate(level, pos, Rotation.CLOCKWISE_90);
            if (candidate != state && candidate.canSurvive(level, pos)) {
                return candidate;
            }
        }
        return null;
    }

    private static void dismantle(ServerLevel level, BlockPos pos, BlockState state,
                                  Player player, InteractionHand hand, ItemStack wrench) {
        ItemStack drop = new ItemStack(state.getBlock().asItem());
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!drop.isEmpty() && blockEntity != null) {
            preserveBlockEntityState(level, blockEntity, drop);
        }

        level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, pos, Block.getId(state));
        boolean removed = level.removeBlock(pos, false);
        if (!removed) {
            return;
        }

        if (!drop.isEmpty() && !player.getAbilities().instabuild) {
            Block.popResource(level, pos, drop);
        }
        damageWrench(wrench, player, hand);
    }

    private static void preserveBlockEntityState(ServerLevel level, BlockEntity blockEntity, ItemStack drop) {
        if (blockEntity instanceof CabinetBlockEntity cabinet) {
            if (!cabinet.hasPreservableState()) {
                return;
            }
            CompoundTag tag = cabinet.saveCustomOnly(level.registryAccess());
            // Installed cards and drives are popped physically by preRemoveSideEffects to prevent duplication.
            tag.remove("Cards");
            tag.remove("Drives");
            if (!tag.isEmpty()) {
                drop.set(DataComponents.BLOCK_ENTITY_DATA, TypedEntityData.of(cabinet.getType(), tag));
            }
            return;
        }

        if (blockEntity instanceof EuAdapterBlockEntity adapter) {
            if (adapter.energy().getAmountAsInt() > 0) {
                CompoundTag tag = adapter.saveCustomOnly(level.registryAccess());
                if (!tag.isEmpty()) {
                    drop.set(DataComponents.BLOCK_ENTITY_DATA, TypedEntityData.of(adapter.getType(), tag));
                }
            }
            return;
        }

        if (blockEntity instanceof TerminalBlockEntity terminal) {
            if (terminal.tapeLines() > 0) {
                CompoundTag tag = new CompoundTag();
                tag.putInt("TapeLines", terminal.tapeLines());
                drop.set(DataComponents.BLOCK_ENTITY_DATA, TypedEntityData.of(terminal.getType(), tag));
            }
            return;
        }

        if (blockEntity instanceof LogisticsPortBlockEntity port) {
            if (port.filter() != null && !port.filter().isEmpty()) {
                CompoundTag tag = port.saveCustomOnly(level.registryAccess());
                if (!tag.isEmpty()) {
                    drop.set(DataComponents.BLOCK_ENTITY_DATA, TypedEntityData.of(port.getType(), tag));
                }
            }
            return;
        }

        if (blockEntity instanceof DisplayPanelBlockEntity panel) {
            if (panel.mode() != DisplayMode.OVERVIEW) {
                CompoundTag tag = new CompoundTag();
                tag.putString("Mode", panel.mode().name());
                drop.set(DataComponents.BLOCK_ENTITY_DATA, TypedEntityData.of(panel.getType(), tag));
            }
        }
    }

    private static void damageWrench(ItemStack wrench, Player player, InteractionHand hand) {
        if (!player.getAbilities().instabuild && wrench.isDamageableItem()) {
            wrench.hurtAndBreak(1, player, hand);
        }
    }
}
