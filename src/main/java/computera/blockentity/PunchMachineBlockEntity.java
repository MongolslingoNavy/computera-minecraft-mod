package computera.blockentity;

import computera.item.ProgramItems;
import computera.registry.ModBlockEntities;
import computera.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class PunchMachineBlockEntity extends BlockEntity {
    public PunchMachineBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PUNCH_MACHINE.get(), pos, state);
    }

    public void describeTo(Player player) {
        message(player, "hold a blank card to punch the file open in the terminal");
    }

    public void useCard(Player player, ItemStack stack, InteractionHand hand) {
        TerminalBlockEntity terminal = adjacentTerminal();
        if (terminal == null) {
            message(player, "no terminal adjacent");
            return;
        }
        if (stack.is(ModItems.BLANK_CARD.get())) {
            String file = terminal.editingFile();
            if (file == null || file.isEmpty()) {
                message(player, "open a file in the terminal first");
                return;
            }
            String source = terminal.fileSource(file);
            if (source == null) {
                message(player, "file not found: " + file);
                return;
            }
            ItemStack card = new ItemStack(ModItems.PUNCHED_CARD.get(), 1);
            ProgramItems.writeCard(card, file, source);
            stack.shrink(1);
            if (stack.isEmpty()) {
                player.setItemInHand(hand, card);
            } else if (!player.getInventory().add(card)) {
                player.drop(card, false);
            }
            message(player, "punched " + file);
        } else if (stack.is(ModItems.PUNCHED_CARD.get())) {
            String name = ProgramItems.singleName(stack);
            String source = ProgramItems.read(stack, name);
            if (name.isEmpty() || source == null) {
                message(player, "card has no program");
                return;
            }
            terminal.saveFileFromCard(name, source);
            message(player, "loaded " + name + " from card");
        } else {
            message(player, "hold a blank or punched card");
        }
    }

    @Nullable
    private TerminalBlockEntity adjacentTerminal() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return null;
        }
        for (Direction direction : Direction.values()) {
            BlockEntity blockEntity = serverLevel.getBlockEntity(worldPosition.relative(direction));
            if (blockEntity instanceof TerminalBlockEntity terminal) {
                return terminal;
            }
        }
        return null;
    }

    private static void message(Player player, String text) {
        player.sendSystemMessage(Component.literal("[Computera] " + text));
    }
}
