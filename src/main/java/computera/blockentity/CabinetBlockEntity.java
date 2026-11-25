package computera.blockentity;

import computera.cluster.Cluster;
import computera.cluster.ClusterManager;
import computera.item.ComputeCardItem;
import computera.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;

import java.util.LinkedHashMap;
import java.util.Map;

public class CabinetBlockEntity extends BlockEntity implements MenuProvider {
    public static final int CARD_SLOTS = 8;
    public static final int DRIVE_SLOTS = 2;
    public static final int ENERGY_CAPACITY = 1_000_000;

    private final NonNullList<ItemStack> cards = NonNullList.withSize(CARD_SLOTS, ItemStack.EMPTY);
    private final NonNullList<ItemStack> drives = NonNullList.withSize(DRIVE_SLOTS, ItemStack.EMPTY);
    private final SimpleEnergyHandler energy = new SimpleEnergyHandler(ENERGY_CAPACITY, 10_000, 10_000);
    private final Map<String, String> files = new LinkedHashMap<>();
    private final Map<String, String> aliases = new LinkedHashMap<>();
    private final java.util.Set<BlockPos> wireless = new java.util.LinkedHashSet<>();
    private final Map<String, String> groups = new LinkedHashMap<>();
    private final java.util.List<String> rules = new java.util.ArrayList<>();
    private final java.util.Map<Integer, Integer> ruleCooldowns = new java.util.HashMap<>();

    public CabinetBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CABINET.get(), pos, state);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.computera.cabinet");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new computera.menu.CabinetMenu(containerId, inventory, worldPosition);
    }

    public SimpleEnergyHandler energy() {
        return energy;
    }

    public ItemStack card(int slot) {
        return cards.get(slot);
    }

    public ItemStack drive(int slot) {
        return drives.get(slot);
    }

    public void setDrive(int slot, ItemStack stack) {
        if (slot < 0 || slot >= DRIVE_SLOTS) {
            return;
        }
        drives.set(slot, stack);
        setChanged();
    }

    public boolean insertDrive(ItemStack stack) {
        if (!stack.is(computera.registry.ModItems.HARD_DRIVE.get())) {
            return false;
        }
        for (int slot = 0; slot < DRIVE_SLOTS; slot++) {
            if (drives.get(slot).isEmpty()) {
                setDrive(slot, stack.copyWithCount(1));
                return true;
            }
        }
        return false;
    }

    public void setCard(int slot, ItemStack stack) {
        if (slot < 0 || slot >= CARD_SLOTS) {
            return;
        }
        cards.set(slot, stack);
        setChanged();
        if (level instanceof ServerLevel serverLevel) {
            ClusterManager.invalidate(serverLevel, worldPosition);
        }
    }

    public boolean insertCard(ItemStack stack) {
        if (!(stack.getItem() instanceof ComputeCardItem)) {
            return false;
        }
        for (int slot = 0; slot < CARD_SLOTS; slot++) {
            if (cards.get(slot).isEmpty()) {
                setCard(slot, stack.copyWithCount(1));
                return true;
            }
        }
        return false;
    }

    public ItemStack popCard() {
        for (int slot = CARD_SLOTS - 1; slot >= 0; slot--) {
            ItemStack stack = cards.get(slot);
            if (!stack.isEmpty()) {
                cards.set(slot, ItemStack.EMPTY);
                setChanged();
                if (level instanceof ServerLevel serverLevel) {
                    ClusterManager.invalidate(serverLevel, worldPosition);
                }
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    public int installedThreads() {
        int threads = 0;
        for (ItemStack stack : cards) {
            if (stack.getItem() instanceof ComputeCardItem card) {
                threads += card.threads();
            }
        }
        return threads;
    }

    public int installedCards() {
        int count = 0;
        for (ItemStack stack : cards) {
            if (!stack.isEmpty()) {
                count++;
            }
        }
        return count;
    }

    public java.util.List<String> rules() {
        return rules;
    }

    public void addRule(String rule) {
        rules.add(rule);
        setChanged();
    }

    public void removeRule(int index) {
        if (index >= 0 && index < rules.size()) {
            rules.remove(index);
            ruleCooldowns.clear();
            setChanged();
        }
    }

    public void toggleRule(int index) {
        if (index < 0 || index >= rules.size()) {
            return;
        }
        String[] p = rules.get(index).split("\\|", 7);
        if (p.length < 7) {
            return;
        }
        p[6] = Boolean.parseBoolean(p[6]) ? "false" : "true";
        rules.set(index, String.join("|", p));
        setChanged();
    }

    public String alias(String key) {
        return aliases.getOrDefault(key, key);
    }

    public void setAlias(String key, String alias) {
        if (alias == null || alias.isBlank()) {
            aliases.remove(key);
        } else {
            aliases.put(key, alias.replace('|', ' ').replace(',', ' '));
        }
        setChanged();
    }

    public String group(String key) {
        return groups.getOrDefault(key, "");
    }

    public void setGroup(String key, String group) {
        if (group == null || group.isBlank()) {
            groups.remove(key);
        } else {
            groups.put(key, group.replace('|', ' ').replace(',', ' '));
        }
        setChanged();
    }

    public java.util.Set<BlockPos> wireless() {
        return wireless;
    }

    public void addWireless(BlockPos pos) {
        wireless.add(pos.immutable());
        setChanged();
    }

    public Map<String, String> files() {
        return files;
    }

    public void putFile(String name, String source) {
        files.put(name, source);
        setChanged();
    }

    public void serverTick() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        ClusterManager.ensureCluster(serverLevel, worldPosition);
        tickRules(serverLevel);
    }

    private void tickRules(ServerLevel level) {
        if (rules.isEmpty()) {
            return;
        }
        Cluster cluster = ClusterManager.clusterFor(level, worldPosition);
        if (cluster == null) {
            return;
        }
        var positions = computera.device.DeviceRegistry.positions(level, cluster);
        for (int i = 0; i < rules.size(); i++) {
            String[] p = rules.get(i).split("\\|", 7);
            if (p.length < 7 || !Boolean.parseBoolean(p[6])) {
                continue;
            }
            int interval = parse(p[5], 20);
            int cooldown = ruleCooldowns.getOrDefault(i, 0);
            if (cooldown > 0) {
                ruleCooldowns.put(i, cooldown - 1);
                continue;
            }
            ruleCooldowns.put(i, interval);
            String type = p[0];
            String from = resolveAlias(p[1]);
            String to = resolveAlias(p[2]);
            String item = p[3];
            int count = parse(p[4], 1);
            if ("move".equals(type)) {
                moveItems(level, positions, from, to, item, count);
            }
        }
    }

    private String resolveAlias(String name) {
        for (Map.Entry<String, String> entry : aliases.entrySet()) {
            if (entry.getValue().equals(name)) {
                return entry.getKey();
            }
        }
        return name;
    }

    private void moveItems(ServerLevel level, java.util.Map<String, BlockPos> positions,
                           String from, String to, String item, int count) {
        var id = net.minecraft.resources.Identifier.tryParse(item);
        if (id == null || count <= 0) {
            return;
        }
        var fromPos = positions.get(from);
        var toPos = positions.get(to);
        if (fromPos == null || toPos == null) {
            return;
        }
        var source = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.Item.BLOCK, fromPos, null);
        var target = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.Item.BLOCK, toPos, null);
        if (source == null || target == null) {
            return;
        }
        var resource = net.neoforged.neoforge.transfer.item.ItemResource.of(
                new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(
                        id)));
        try (var transaction = net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
            if (resource.isEmpty()) {
                return;
            }
            int accepted;
            try (var probe = net.neoforged.neoforge.transfer.transaction.Transaction.open(transaction)) {
                accepted = target.insert(resource, Math.min(count, 64), probe);
            }
            int extracted = source.extract(resource, accepted, transaction);
            int inserted = extracted > 0 ? target.insert(resource, extracted, transaction) : 0;
            if (inserted != extracted) {
                inserted = 0;
            }
            if (inserted > 0) {
                transaction.commit();
            }
        }
    }

    private static int parse(String text, int fallback) {
        try {
            return Math.max(1, Integer.parseInt(text.trim()));
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    public void describeTo(Player player) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        Cluster cluster = ClusterManager.clusterFor(serverLevel, worldPosition);
        int cabinets = cluster == null ? 1 : cluster.cabinetCount();
        int threads = cluster == null ? installedThreads() : cluster.threadCapacity();
        player.sendSystemMessage(Component.literal(
                "[Computera] cabinets=" + cabinets
                        + " cards=" + installedCards()
                        + " threads=" + threads
                        + " files=" + files.size()
                        + " energy=" + energy.getAmountAsLong()));
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        var cardList = output.list("Cards", ItemStack.OPTIONAL_CODEC);
        for (ItemStack stack : cards) {
            cardList.add(stack);
        }
        var driveList = output.list("Drives", ItemStack.OPTIONAL_CODEC);
        for (ItemStack stack : drives) {
            driveList.add(stack);
        }
        energy.serialize(output.child("Energy"));
        var aliasList = output.childrenList("Aliases");
        for (Map.Entry<String, String> entry : aliases.entrySet()) {
            ValueOutput child = aliasList.addChild();
            child.putString("Key", entry.getKey());
            child.putString("Alias", entry.getValue());
        }
        var ruleList = output.list("Rules", com.mojang.serialization.Codec.STRING);
        for (String rule : rules) {
            ruleList.add(rule);
        }
        var groupList = output.childrenList("Groups");
        for (Map.Entry<String, String> entry : groups.entrySet()) {
            ValueOutput child = groupList.addChild();
            child.putString("Key", entry.getKey());
            child.putString("Group", entry.getValue());
        }
        var wirelessList = output.childrenList("Wireless");
        for (BlockPos pos : wireless) {
            ValueOutput child = wirelessList.addChild();
            child.putInt("X", pos.getX());
            child.putInt("Y", pos.getY());
            child.putInt("Z", pos.getZ());
        }
        var fileList = output.childrenList("Files");
        for (Map.Entry<String, String> entry : files.entrySet()) {
            ValueOutput child = fileList.addChild();
            child.putString("Name", entry.getKey());
            child.putString("Source", entry.getValue());
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        cards.clear();
        int slot = 0;
        for (ItemStack stack : input.listOrEmpty("Cards", ItemStack.OPTIONAL_CODEC)) {
            if (slot >= CARD_SLOTS) {
                break;
            }
            cards.set(slot++, stack);
        }
        drives.clear();
        int driveSlot = 0;
        for (ItemStack stack : input.listOrEmpty("Drives", ItemStack.OPTIONAL_CODEC)) {
            if (driveSlot >= DRIVE_SLOTS) {
                break;
            }
            drives.set(driveSlot++, stack);
        }
        input.child("Energy").ifPresent(energy::deserialize);
        aliases.clear();
        for (ValueInput child : input.childrenListOrEmpty("Aliases")) {
            aliases.put(child.getStringOr("Key", ""), child.getStringOr("Alias", ""));
        }
        rules.clear();
        input.listOrEmpty("Rules", com.mojang.serialization.Codec.STRING).forEach(rules::add);
        groups.clear();
        for (ValueInput child : input.childrenListOrEmpty("Groups")) {
            groups.put(child.getStringOr("Key", ""), child.getStringOr("Group", ""));
        }
        wireless.clear();
        for (ValueInput child : input.childrenListOrEmpty("Wireless")) {
            wireless.add(new BlockPos(child.getIntOr("X", 0), child.getIntOr("Y", 0), child.getIntOr("Z", 0)));
        }
        files.clear();
        for (ValueInput child : input.childrenListOrEmpty("Files")) {
            files.put(child.getStringOr("Name", ""), child.getStringOr("Source", ""));
        }
    }
}
