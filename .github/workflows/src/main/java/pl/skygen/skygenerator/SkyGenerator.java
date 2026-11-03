package pl.skygen.skygenerator;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class SkyGenerator extends JavaPlugin implements CommandExecutor, TabCompleter {

    public static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final Map<String, GeneratorType> types = new LinkedHashMap<>();
    private final Map<BlockKey, PlacedGenerator> placed = new HashMap<>();

    private NamespacedKey typeKey;
    private File dataFile;
    private ArmorManager armor;
    private GuiManager gui;
    private int maxPerPlayer;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        typeKey = new NamespacedKey(this, "generator_type");
        dataFile = new File(getDataFolder(), "data.yml");
        armor = new ArmorManager(this);
        gui = new GuiManager(this);

        loadSettings();
        loadData();

        getServer().getPluginManager().registerEvents(gui, this);
        getServer().getPluginManager().registerEvents(new GeneratorListener(this), this);

        getCommand("skygenerator").setExecutor(this);
        getCommand("skygenerator").setTabCompleter(this);

        getServer().getScheduler().runTaskTimer(this, this::tick, 20L, 20L);
        getServer().getScheduler().runTaskTimer(this, this::saveData, 6000L, 6000L);

        getLogger().info("SkyGenerator włączony. Typów: " + types.size()
                + ", postawionych generatorów: " + placed.size());
    }

    @Override
    public void onDisable() {
        saveData();
    }

    // ---------------- konfiguracja i dane ----------------

    private void loadSettings() {
        reloadConfig();
        types.clear();
        maxPerPlayer = getConfig().getInt("max-per-player", 30);

        ConfigurationSection root = getConfig().getConfigurationSection("generators");
        if (root != null) {
            for (String id : root.getKeys(false)) {
                ConfigurationSection s = root.getConfigurationSection(id);
                if (s == null) continue;
                Material block = Material.matchMaterial(s.getString("block", "STONE"));
                Material product = Material.matchMaterial(s.getString("produces", "COBBLESTONE"));
                Material cost = Material.matchMaterial(s.getString("cost.material", "COBBLESTONE"));
                if (block == null || !block.isBlock() || product == null || cost == null) {
                    getLogger().warning("Pominięto generator '" + id + "' - błędny materiał w configu.");
                    continue;
                }
                String key = id.toLowerCase(Locale.ROOT);
                types.put(key, new GeneratorType(
                        key,
                        s.getString("name", id),
                        block,
                        product,
                        Math.max(1, s.getInt("amount", 1)),
                        Math.max(1, s.getInt("interval", 5)),
                        Math.max(1, s.getInt("capacity", 256)),
                        cost,
                        s.getInt("cost.amount", 0),
                        s.getDouble("armor-points", 1)));
            }
        }
        armor.reload();
    }

    private void loadData() {
        placed.clear();
        if (!dataFile.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(dataFile);
        ConfigurationSection root = y.getConfigurationSection("generators");
        if (root == null) return;
        for (String k : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(k);
            if (s == null) continue;
            try {
                BlockKey key = new BlockKey(UUID.fromString(s.getString("world", "")),
                        s.getInt("x"), s.getInt("y"), s.getInt("z"));
                PlacedGenerator g = new PlacedGenerator(key,
                        UUID.fromString(s.getString("owner", "")),
                        s.getString("type", ""));
                g.stored = s.getInt("stored");
                g.timer = s.getInt("timer");
                placed.put(key, g);
            } catch (IllegalArgumentException ex) {
                getLogger().warning("Pominięto uszkodzony wpis generatora: " + k);
            }
        }
    }

    public void saveData() {
        YamlConfiguration y = new YamlConfiguration();
        int i = 0;
        for (PlacedGenerator g : placed.values()) {
            String p = "generators." + (i++) + ".";
            y.set(p + "world", g.key.world().toString());
            y.set(p + "x", g.key.x());
            y.set(p + "y", g.key.y());
            y.set(p + "z", g.key.z());
            y.set(p + "owner", g.owner.toString());
            y.set(p + "type", g.typeId);
            y.set(p + "stored", g.stored);
            y.set(p + "timer", g.timer);
        }
        try {
            y.save(dataFile);
        } catch (IOException ex) {
            getLogger().severe("Nie udało się zapisać data.yml: " + ex.getMessage());
        }
    }

    // ---------------- główna pętla ----------------

    private void tick() {
        for (PlacedGenerator g : placed.values()) {
            GeneratorType t = types.get(g.typeId);
            if (t == null) continue;
            World w = Bukkit.getWorld(g.key.world());
            if (w == null || !w.isChunkLoaded(g.key.x() >> 4, g.key.z() >> 4)) continue;
            if (g.stored >= t.capacity()) {
                g.timer = 0;
                continue;
            }
            g.timer++;
            if (g.timer >= t.interval()) {
                g.timer = 0;
                g.stored = Math.min(t.capacity(), g.stored + t.amount());
            }
        }

        if (armor.isEnabled()) {
            for (Player p : getServer().getOnlinePlayers()) {
                if (p.getGameMode() == GameMode.SPECTATOR) continue;
                if (armor.requireOnGround() && (!p.isOnGround() || p.isFlying())) continue;
                Block below = p.getLocation().subtract(0, 0.1, 0).getBlock();
                PlacedGenerator g = placed.get(BlockKey.of(below));
                if (g == null) continue;
                GeneratorType t = types.get(g.typeId);
                if (t == null || t.armorPoints() <= 0) continue;
                armor.process(p, t.armorPoints());
            }
        }

        gui.refreshOpen();
    }

    // ---------------- API dla listenerów ----------------

    public Map<String, GeneratorType> types() {
        return types;
    }

    public GeneratorType type(String id) {
        return id == null ? null : types.get(id);
    }

    public PlacedGenerator generator(BlockKey key) {
        return placed.get(key);
    }

    public void register(PlacedGenerator g) {
        placed.put(g.key, g);
    }

    public void unregister(BlockKey key) {
        placed.remove(key);
    }

    public int countOwned(UUID owner) {
        int c = 0;
        for (PlacedGenerator g : placed.values()) {
            if (g.owner.equals(owner)) c++;
        }
        return c;
    }

    public int maxPerPlayer() {
        return maxPerPlayer;
    }

    public GuiManager gui() {
        return gui;
    }

    public ItemStack createItem(GeneratorType t, int amount) {
        ItemStack it = new ItemStack(t.block(), amount);
        it.editMeta(m -> {
            m.displayName(noItalic(t.name()));
            m.lore(List.of(
                    noItalic("&7Postaw, aby uruchomić generator."),
                    noItalic("&7Produkuje: &f" + t.amount() + "x " + pretty(t.product())
                            + " &7co &f" + t.interval() + "s")));
            m.getPersistentDataContainer().set(typeKey, PersistentDataType.STRING, t.id());
        });
        return it;
    }

    public String typeIdOf(ItemStack item) {
        if (item == null || item.getType().isAir()) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        return meta.getPersistentDataContainer().get(typeKey, PersistentDataType.STRING);
    }

    public void giveOrDrop(Player p, ItemStack item) {
        Map<Integer, ItemStack> left = p.getInventory().addItem(item);
        for (ItemStack rest : left.values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), rest);
        }
    }

    // ---------------- teksty ----------------

    public String raw(String key, String... kv) {
        String s = getConfig().getString("messages." + key, key);
        for (int i = 0; i + 1 < kv.length; i += 2) {
            s = s.replace(kv[i], kv[i + 1]);
        }
        return s;
    }

    public Component plain(String key, String... kv) {
        return LEGACY.deserialize(raw(key, kv));
    }

    public Component msg(String key, String... kv) {
        return LEGACY.deserialize(raw("prefix") + raw(key, kv));
    }

    public static Component noItalic(String legacy) {
        return LEGACY.deserialize(legacy).decoration(TextDecoration.ITALIC, false);
    }

    public static String pretty(Material m) {
        String[] parts = m.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }

    // ---------------- komendy ----------------

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage(msg("player-only"));
                return true;
            }
            if (!p.hasPermission("skygenerator.use")) {
                p.sendMessage(msg("no-permission"));
                return true;
            }
            gui.openShop(p);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("reload")) {
            if (!sender.hasPermission("skygenerator.admin")) {
                sender.sendMessage(msg("no-permission"));
                return true;
            }
            loadSettings();
            sender.sendMessage(msg("reloaded"));
            return true;
        }

        if (sub.equals("give")) {
            if (!sender.hasPermission("skygenerator.admin")) {
                sender.sendMessage(msg("no-permission"));
                return true;
            }
            if (args.length < 3) {
                sender.sendMessage(msg("usage-give"));
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[1]);
            GeneratorType t = types.get(args[2].toLowerCase(Locale.ROOT));
            if (target == null || t == null) {
                sender.sendMessage(msg("usage-give"));
                return true;
            }
            int amount = 1;
            if (args.length >= 4) {
                try {
                    amount = Math.max(1, Math.min(64, Integer.parseInt(args[3])));
                } catch (NumberFormatException ex) {
                    sender.sendMessage(msg("usage-give"));
                    return true;
                }
            }
            giveOrDrop(target, createItem(t, amount));
            sender.sendMessage(msg("gave",
                    "%amount%", String.valueOf(amount),
                    "%name%", t.name(),
                    "%player%", target.getName()));
            return true;
        }
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (!sender.hasPermission("skygenerator.admin")) return out;
        if (args.length == 1) {
            out.add("give");
            out.add("reload");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
        } else if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            out.addAll(types.keySet());
        }
        String last = args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }
}
