package pl.skygen.skygenerator;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Ulepszanie zbroi gracza stojącego na generatorze. */
public final class ArmorManager {

    private final SkyGenerator plugin;
    private final NamespacedKey levelKey;
    private final NamespacedKey progressKey;

    private boolean enabled;
    private int maxLevel;
    private double basePoints;
    private double multiplier;
    private boolean requireOnGround;

    public ArmorManager(SkyGenerator plugin) {
        this.plugin = plugin;
        this.levelKey = new NamespacedKey(plugin, "armor_level");
        this.progressKey = new NamespacedKey(plugin, "armor_progress");
    }

    public void reload() {
        FileConfiguration c = plugin.getConfig();
        enabled = c.getBoolean("armor.enabled", true);
        maxLevel = Math.max(1, c.getInt("armor.max-level", 5));
        basePoints = c.getDouble("armor.base-points", 60);
        multiplier = c.getDouble("armor.multiplier", 1.5);
        requireOnGround = c.getBoolean("armor.require-on-ground", true);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean requireOnGround() {
        return requireOnGround;
    }

    public void process(Player p, double points) {
        ItemStack[] armor = p.getInventory().getArmorContents();
        boolean anyArmor = false;
        boolean changed = false;
        double nearestProgress = -1;
        double nearestNeeded = -1;

        for (int i = 0; i < armor.length; i++) {
            ItemStack item = armor[i];
            if (!isArmor(item)) continue;
            anyArmor = true;

            int level = getLevel(item);
            if (level >= maxLevel) {
                continue;
            }

            double progress = getProgress(item) + points;
            double needed = needed(level);
            boolean leveled = false;
            while (progress >= needed && level < maxLevel) {
                progress -= needed;
                level++;
                leveled = true;
                needed = needed(level);
            }
            if (level >= maxLevel) progress = 0;

            applyData(item, level, progress);
            changed = true;

            if (leveled) {
                p.sendMessage(plugin.msg("armor-level-up",
                        "%piece%", prettyPiece(item.getType()),
                        "%level%", String.valueOf(level)));
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
            }

            if (level < maxLevel && (nearestNeeded < 0
                    || (needed - progress) < (nearestNeeded - nearestProgress))) {
                nearestProgress = progress;
                nearestNeeded = needed;
            }
        }

        if (changed) p.getInventory().setArmorContents(armor);

        if (!anyArmor) {
            p.sendActionBar(plugin.plain("armor-no-armor"));
        } else if (nearestNeeded < 0) {
            p.sendActionBar(plugin.plain("armor-actionbar-maxed"));
        } else {
            p.sendActionBar(plugin.plain("armor-actionbar",
                    "%points%", fmt(points),
                    "%progress%", fmt(nearestProgress),
                    "%needed%", fmt(nearestNeeded)));
        }
    }

    private double needed(int currentLevel) {
        return basePoints * Math.pow(multiplier, currentLevel);
    }

    private boolean isArmor(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        String n = item.getType().name();
        return n.endsWith("_HELMET") || n.endsWith("_CHESTPLATE")
                || n.endsWith("_LEGGINGS") || n.endsWith("_BOOTS");
    }

    private int getLevel(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return 0;
        return meta.getPersistentDataContainer().getOrDefault(levelKey, PersistentDataType.INTEGER, 0);
    }

    private double getProgress(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return 0;
        return meta.getPersistentDataContainer().getOrDefault(progressKey, PersistentDataType.DOUBLE, 0.0);
    }

    private void applyData(ItemStack item, int level, double progress) {
        item.editMeta(meta -> {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(levelKey, PersistentDataType.INTEGER, level);
            pdc.set(progressKey, PersistentDataType.DOUBLE, progress);
            if (level > 0) {
                meta.addEnchant(Enchantment.PROTECTION, level, true);
            }
            meta.lore(buildLore(level, progress));
        });
    }

    private List<Component> buildLore(int level, double progress) {
        List<Component> lore = new ArrayList<>();
        lore.add(SkyGenerator.noItalic(plugin.raw("lore-level",
                "%level%", String.valueOf(level),
                "%max%", String.valueOf(maxLevel))));
        if (level >= maxLevel) {
            lore.add(SkyGenerator.noItalic(plugin.raw("lore-maxed")));
        } else {
            lore.add(SkyGenerator.noItalic(plugin.raw("lore-progress",
                    "%progress%", fmt(progress),
                    "%needed%", fmt(needed(level)))));
        }
        return lore;
    }

    private String prettyPiece(Material m) {
        String n = m.name();
        if (n.endsWith("_HELMET")) return "Hełm";
        if (n.endsWith("_CHESTPLATE")) return "Napierśnik";
        if (n.endsWith("_LEGGINGS")) return "Nogawice";
        if (n.endsWith("_BOOTS")) return "Buty";
        return n;
    }

    private String fmt(double d) {
        return String.format(Locale.US, "%.0f", d);
    }
}
