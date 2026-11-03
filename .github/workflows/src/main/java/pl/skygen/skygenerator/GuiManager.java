package pl.skygen.skygenerator;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Sklep z generatorami oraz magazyn pojedynczego generatora. */
public final class GuiManager implements Listener {

    private static final int SLOT_TAKE_STACK = 11;
    private static final int SLOT_INFO = 13;
    private static final int SLOT_TAKE_ALL = 15;

    private final SkyGenerator plugin;

    public GuiManager(SkyGenerator plugin) {
        this.plugin = plugin;
    }

    private static final class ShopHolder implements InventoryHolder {
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static final class StorageHolder implements InventoryHolder {
        private final BlockKey key;
        private Inventory inventory;

        StorageHolder(BlockKey key) {
            this.key = key;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    // ---------------- SKLEP ----------------

    public void openShop(Player p) {
        List<GeneratorType> list = new ArrayList<>(plugin.types().values());
        int size = Math.max(9, Math.min(54, ((list.size() + 8) / 9) * 9));
        ShopHolder holder = new ShopHolder();
        Inventory inv = Bukkit.createInventory(holder, size, plugin.plain("gui-shop-title"));
        holder.inventory = inv;
        for (int i = 0; i < list.size() && i < size; i++) {
            inv.setItem(i, shopIcon(list.get(i)));
        }
        p.openInventory(inv);
    }

    private ItemStack shopIcon(GeneratorType t) {
        ItemStack it = new ItemStack(t.block());
        it.editMeta(m -> {
            m.displayName(SkyGenerator.noItalic(t.name()));
            List<Component> lore = new ArrayList<>();
            lore.add(SkyGenerator.noItalic("&7Produkuje: &f" + t.amount() + "x "
                    + SkyGenerator.pretty(t.product()) + " &7co &f" + t.interval() + "s"));
            lore.add(SkyGenerator.noItalic("&7Pojemność magazynu: &f" + t.capacity()));
            lore.add(SkyGenerator.noItalic("&7Punkty zbroi: &f" + (int) t.armorPoints() + "/s"));
            lore.add(Component.empty());
            if (t.costAmount() > 0) {
                lore.add(SkyGenerator.noItalic("&7Koszt: &e" + t.costAmount() + "x "
                        + SkyGenerator.pretty(t.costMaterial())));
            } else {
                lore.add(SkyGenerator.noItalic("&7Koszt: &aza darmo"));
            }
            lore.add(SkyGenerator.noItalic("&aKliknij, aby kupić"));
            m.lore(lore);
        });
        return it;
    }

    private void buy(Player p, int slot) {
        List<GeneratorType> list = new ArrayList<>(plugin.types().values());
        if (slot < 0 || slot >= list.size()) return;
        GeneratorType t = list.get(slot);
        if (!p.hasPermission("skygenerator.use")) {
            p.sendMessage(plugin.msg("no-permission"));
            return;
        }

        if (t.costAmount() > 0) {
            if (!p.getInventory().containsAtLeast(new ItemStack(t.costMaterial()), t.costAmount())) {
                p.sendMessage(plugin.msg("not-enough",
                        "%amount%", String.valueOf(t.costAmount()),
                        "%item%", SkyGenerator.pretty(t.costMaterial())));
                p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                return;
            }
            p.getInventory().removeItem(new ItemStack(t.costMaterial(), t.costAmount()));
        }

        plugin.giveOrDrop(p, plugin.createItem(t, 1));
        p.sendMessage(plugin.msg("bought", "%name%", t.name()));
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
    }

    // ---------------- MAGAZYN ----------------

    public void openStorage(Player p, PlacedGenerator g) {
        GeneratorType t = plugin.type(g.typeId);
        if (t == null) {
            p.sendMessage(plugin.msg("unknown-type"));
            return;
        }
        StorageHolder holder = new StorageHolder(g.key);
        Inventory inv = Bukkit.createInventory(holder, 27, plugin.plain("gui-storage-title"));
        holder.inventory = inv;
        render(inv, g, t);
        p.openInventory(inv);
    }

    private void render(Inventory inv, PlacedGenerator g, GeneratorType t) {
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        pane.editMeta(m -> m.displayName(Component.text(" ")));
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, pane);

        int max = t.product().getMaxStackSize();

        ItemStack stack = new ItemStack(Material.CHEST);
        stack.editMeta(m -> {
            m.displayName(SkyGenerator.noItalic("&aWyciągnij 1 stack"));
            m.lore(List.of(SkyGenerator.noItalic("&7Wyciąga do &f" + max + " &7sztuk")));
        });
        inv.setItem(SLOT_TAKE_STACK, stack);

        ItemStack info = new ItemStack(t.product());
        info.editMeta(m -> {
            m.displayName(SkyGenerator.noItalic(t.name()));
            m.lore(List.of(
                    SkyGenerator.noItalic("&7W magazynie: &e" + g.stored + "&7/&e" + t.capacity()),
                    SkyGenerator.noItalic("&7Produkcja: &f" + t.amount() + "x "
                            + SkyGenerator.pretty(t.product()) + " &7co &f" + t.interval() + "s")));
        });
        inv.setItem(SLOT_INFO, info);

        ItemStack all = new ItemStack(Material.HOPPER);
        all.editMeta(m -> {
            m.displayName(SkyGenerator.noItalic("&6Wyciągnij wszystko"));
            m.lore(List.of(SkyGenerator.noItalic("&7Wyciąga &e" + g.stored + " &7sztuk")));
        });
        inv.setItem(SLOT_TAKE_ALL, all);
    }

    private void take(Player p, PlacedGenerator g, GeneratorType t, int request) {
        int want = Math.min(request, g.stored);
        if (want <= 0) {
            p.sendMessage(plugin.msg("storage-empty"));
            return;
        }
        int maxStack = t.product().getMaxStackSize();
        int remaining = want;
        int given = 0;
        boolean full = false;
        while (remaining > 0) {
            int n = Math.min(maxStack, remaining);
            Map<Integer, ItemStack> left = p.getInventory().addItem(new ItemStack(t.product(), n));
            int notFit = 0;
            for (ItemStack s : left.values()) notFit += s.getAmount();
            given += n - notFit;
            remaining -= n;
            if (notFit > 0) {
                full = true;
                break;
            }
        }
        g.stored -= given;
        if (given > 0) {
            p.sendMessage(plugin.msg("withdrawn",
                    "%amount%", String.valueOf(given),
                    "%item%", SkyGenerator.pretty(t.product())));
            p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 1f);
        }
        if (full) p.sendMessage(plugin.msg("inventory-full"));
    }

    /** Odświeża otwarte magazyny (produkcja trwa w tle). */
    public void refreshOpen() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            InventoryHolder h = p.getOpenInventory().getTopInventory().getHolder();
            if (h instanceof StorageHolder sh) {
                PlacedGenerator g = plugin.generator(sh.key);
                GeneratorType t = g == null ? null : plugin.type(g.typeId);
                if (g == null || t == null) {
                    p.closeInventory();
                } else {
                    render(sh.inventory, g, t);
                }
            }
        }
    }

    // ---------------- ZDARZENIA ----------------

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        InventoryHolder holder = e.getInventory().getHolder();
        if (!(holder instanceof ShopHolder) && !(holder instanceof StorageHolder)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() != e.getInventory()) return;

        int slot = e.getRawSlot();
        if (holder instanceof ShopHolder) {
            buy(p, slot);
        } else if (holder instanceof StorageHolder sh) {
            PlacedGenerator g = plugin.generator(sh.key);
            GeneratorType t = g == null ? null : plugin.type(g.typeId);
            if (g == null || t == null) {
                p.closeInventory();
                return;
            }
            if (slot == SLOT_TAKE_STACK) {
                take(p, g, t, t.product().getMaxStackSize());
            } else if (slot == SLOT_TAKE_ALL) {
                take(p, g, t, Integer.MAX_VALUE);
            }
            render(sh.inventory, g, t);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        InventoryHolder holder = e.getInventory().getHolder();
        if (holder instanceof ShopHolder || holder instanceof StorageHolder) {
            e.setCancelled(true);
        }
    }
}
