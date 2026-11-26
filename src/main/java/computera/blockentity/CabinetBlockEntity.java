package computera.blockentity;

import computera.cluster.Cluster;
import computera.cluster.ClusterManager;
import computera.device.DeviceRegistry;
import computera.item.ComputeCardItem;
import computera.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public class CabinetBlockEntity extends BlockEntity implements MenuProvider {
    public static final int CARD_SLOTS = 8;
    public static final int DRIVE_SLOTS = 2;
    public static final int ENERGY_CAPACITY = 1_000_000;
    public static final int MAX_WIRELESS = 64;
    public static final int MAX_FILES = 64;
    public static final int MAX_FILE_SIZE = 16_384;
    public static final int MAX_FILE_NAME = 64;
    public static final int MAX_ALIASES = 256;
    public static final int MAX_GROUPS = 256;
    public static final int MAX_ALIAS_LEN = 32;
    public static final int MAX_GROUP_LEN = 16;

    private final NonNullList<ItemStack> cards = NonNullList.withSize(CARD_SLOTS, ItemStack.EMPTY);
    private final NonNullList<ItemStack> drives = NonNullList.withSize(DRIVE_SLOTS, ItemStack.EMPTY);
    private final SimpleEnergyHandler energy = new SimpleEnergyHandler(ENERGY_CAPACITY, 10_000, 10_000);
    private final Map<String, String> files = new LinkedHashMap<>();
    private final Map<String, String> aliases = new LinkedHashMap<>();
    private final Map<BlockPos, @Nullable Direction> wirelessFaces = new LinkedHashMap<>();
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
        if (key == null || key.isBlank()) {
            return;
        }
        if (alias == null || alias.isBlank()) {
            aliases.remove(key);
        } else {
            if (!aliases.containsKey(key) && aliases.size() >= MAX_ALIASES) {
                return;
            }
            String clean = alias.replace('|', ' ').replace(',', ' ').trim();
            if (clean.length() > MAX_ALIAS_LEN) {
                clean = clean.substring(0, MAX_ALIAS_LEN);
            }
            if (clean.isEmpty()) {
                aliases.remove(key);
            } else {
                aliases.put(key, clean);
            }
        }
        setChanged();
    }

    public String group(String key) {
        return groups.getOrDefault(key, "");
    }

    public void setGroup(String key, String group) {
        if (key == null || key.isBlank()) {
            return;
        }
        if (group == null || group.isBlank()) {
            groups.remove(key);
        } else {
            if (!groups.containsKey(key) && groups.size() >= MAX_GROUPS) {
                return;
            }
            String clean = group.replace('|', ' ').replace(',', ' ').trim();
            if (clean.length() > MAX_GROUP_LEN) {
                clean = clean.substring(0, MAX_GROUP_LEN);
            }
            if (clean.isEmpty()) {
                groups.remove(key);
            } else {
                groups.put(key, clean);
            }
        }
        setChanged();
    }

    public java.util.Set<BlockPos> wireless() {
        return Collections.unmodifiableSet(wirelessFaces.keySet());
    }

    @Nullable
    public Direction wirelessFace(BlockPos pos) {
        return wirelessFaces.get(pos);
    }

    public boolean addWireless(BlockPos pos) {
        return addWireless(pos, null);
    }

    public boolean addWireless(BlockPos pos, @Nullable Direction face) {
        BlockPos immutable = pos.immutable();
        if (!wirelessFaces.containsKey(immutable) && wirelessFaces.size() >= MAX_WIRELESS) {
            return false;
        }
        wirelessFaces.put(immutable, face);
        setChanged();
        if (level instanceof ServerLevel serverLevel) {
            ClusterManager.invalidate(serverLevel, worldPosition);
        }
        return true;
    }

    public boolean removeWireless(BlockPos pos) {
        if (wirelessFaces.containsKey(pos)) {
            wirelessFaces.remove(pos);
            setChanged();
            if (level instanceof ServerLevel serverLevel) {
                ClusterManager.invalidate(serverLevel, worldPosition);
            }
            return true;
        }
        return false;
    }

    public void pruneWireless(ServerLevel serverLevel) {
        boolean removed = false;
        Iterator<Map.Entry<BlockPos, Direction>> iterator = wirelessFaces.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<BlockPos, Direction> entry = iterator.next();
            BlockPos pos = entry.getKey();
            if (!serverLevel.hasChunkAt(pos)) {
                continue;
            }
            Direction face = entry.getValue();
            boolean valid = serverLevel.getCapability(Capabilities.Item.BLOCK, pos, face) != null
                    || (face != null && serverLevel.getCapability(Capabilities.Item.BLOCK, pos, null) != null);
            if (!valid) {
                iterator.remove();
                removed = true;
            }
        }
        if (removed) {
            setChanged();
            ClusterManager.invalidate(serverLevel, worldPosition);
        }
    }

    public Map<String, String> files() {
        return files;
    }

    public boolean putFile(String name, String source) {
        if (name == null || name.isBlank() || name.length() > MAX_FILE_NAME) {
            return false;
        }
        String text = source == null ? "" : source;
        if (text.length() > MAX_FILE_SIZE) {
            return false;
        }
        if (!files.containsKey(name) && files.size() >= MAX_FILES) {
            return false;
        }
        files.put(name, text);
        setChanged();
        return true;
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level instanceof ServerLevel serverLevel) {
            for (int i = 0; i < CARD_SLOTS; i++) {
                ItemStack stack = cards.get(i);
                if (!stack.isEmpty()) {
                    Block.popResource(serverLevel, pos, stack);
                    cards.set(i, ItemStack.EMPTY);
                }
            }
            for (int i = 0; i < DRIVE_SLOTS; i++) {
                ItemStack stack = drives.get(i);
                if (!stack.isEmpty()) {
                    Block.popResource(serverLevel, pos, stack);
                    drives.set(i, ItemStack.EMPTY);
                }
            }
            setChanged();
        }
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
        var entries = DeviceRegistry.entries(level, cluster);
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
                moveItems(level, entries, from, to, item, count);
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

    private void moveItems(ServerLevel level, Map<String, DeviceRegistry.DeviceEntry> entries,
                           String from, String to, String item, int count) {
        Identifier id = Identifier.tryParse(item);
        if (id == null || count <= 0) {
            return;
        }
        var fromEntry = entries.get(from);
        var toEntry = entries.get(to);
        if (fromEntry == null || toEntry == null || fromEntry.pos().equals(toEntry.pos())) {
            return;
        }
        if (level.getBlockEntity(fromEntry.pos()) instanceof CrateBlockEntity fromCrate
                && level.getBlockEntity(toEntry.pos()) instanceof CrateBlockEntity toCrate
                && fromCrate.groupRoot().equals(toCrate.groupRoot())) {
            return;
        }
        var source = DeviceRegistry.itemHandler(level, fromEntry);
        var target = DeviceRegistry.itemHandler(level, toEntry);
        if (source == null || target == null) {
            return;
        }
        String targetId = id.toString();
        int remaining = Math.min(count, 64);
        for (int slot = 0; slot < source.size() && remaining > 0; slot++) {
            ItemResource resource = source.getResource(slot);
            if (resource.isEmpty()) {
                continue;
            }
            String slotId = BuiltInRegistries.ITEM.getKey(resource.toStack(1).getItem()).toString();
            if (!targetId.equals(slotId)) {
                continue;
            }
            int want = (int) Math.min(source.getAmountAsLong(slot), remaining);
            if (want <= 0) {
                continue;
            }
            try (Transaction transaction = Transaction.openRoot()) {
                int accepted;
                try (Transaction probe = Transaction.open(transaction)) {
                    accepted = target.insert(resource, want, probe);
                }
                if (accepted <= 0) {
                    continue;
                }
                int extracted = source.extract(slot, resource, accepted, transaction);
                int inserted = extracted > 0 ? target.insert(resource, extracted, transaction) : 0;
                if (inserted == extracted && inserted > 0) {
                    transaction.commit();
                    remaining -= inserted;
                }
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
        for (Map.Entry<BlockPos, Direction> entry : wirelessFaces.entrySet()) {
            BlockPos pos = entry.getKey();
            ValueOutput child = wirelessList.addChild();
            child.putInt("X", pos.getX());
            child.putInt("Y", pos.getY());
            child.putInt("Z", pos.getZ());
            if (entry.getValue() != null) {
                child.putString("Face", entry.getValue().getName());
            }
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
        wirelessFaces.clear();
        for (ValueInput child : input.childrenListOrEmpty("Wireless")) {
            if (wirelessFaces.size() >= MAX_WIRELESS) {
                break;
            }
            BlockPos pos = new BlockPos(child.getIntOr("X", 0), child.getIntOr("Y", 0), child.getIntOr("Z", 0));
            String faceName = child.getStringOr("Face", "");
            Direction face = faceName.isEmpty() ? null : Direction.byName(faceName);
            wirelessFaces.put(pos, face);
        }
        files.clear();
        for (ValueInput child : input.childrenListOrEmpty("Files")) {
            files.put(child.getStringOr("Name", ""), child.getStringOr("Source", ""));
        }
    }
}
