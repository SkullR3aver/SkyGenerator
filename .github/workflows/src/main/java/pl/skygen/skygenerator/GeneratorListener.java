package pl.skygen.skygenerator;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class GeneratorListener implements Listener {

    private final SkyGenerator plugin;

    public GeneratorListener(SkyGenerator plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onPlace(BlockPlaceEvent e) {
        String id = plugin.typeIdOf(e.getItemInHand());
        if (id == null) return;

        Player p = e.getPlayer();
        GeneratorType t = plugin.type(id);
        if (t == null) {
            e.setCancelled(true);
            p.sendMessage(plugin.msg("unknown-type"));
            return;
        }

        int max = plugin.maxPerPlayer();
        if (max > 0 && !p.hasPermission("skygenerator.bypass")
                && plugin.countOwned(p.getUniqueId()) >= max) {
            e.setCancelled(true);
            p.sendMessage(plugin.msg("limit", "%max%", String.valueOf(max)));
            return;
        }

        plugin.register(new PlacedGenerator(BlockKey.of(e.getBlockPlaced()), p.getUniqueId(), t.id()));
        p.sendMessage(plugin.msg("placed", "%name%", t.name()));
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBreak(BlockBreakEvent e) {
        BlockKey key = BlockKey.of(e.getBlock());
        PlacedGenerator g = plugin.generator(key);
        if (g == null) return;

        Player p = e.getPlayer();
        if (!g.owner.equals(p.getUniqueId()) && !p.hasPermission("skygenerator.bypass")) {
            e.setCancelled(true);
            p.sendMessage(plugin.msg("not-owner"));
            return;
        }
        GeneratorType t = plugin.type(g.typeId);
        if (t == null) {
            e.setCancelled(true);
            p.sendMessage(plugin.msg("unknown-type"));
            return;
        }

        e.setDropItems(false);
        plugin.unregister(key);

        List<ItemStack> give = new ArrayList<>();
        give.add(plugin.createItem(t, 1));
        int left = g.stored;
        int maxStack = t.product().getMaxStackSize();
        while (left > 0) {
            int n = Math.min(maxStack, left);
            give.add(new ItemStack(t.product(), n));
            left -= n;
        }
        for (ItemStack it : give) {
            plugin.giveOrDrop(p, it);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getHand() != EquipmentSlot.HAND) return;
        if (e.useInteractedBlock() == Event.Result.DENY) return;
        Block b = e.getClickedBlock();
        if (b == null) return;
        Player p = e.getPlayer();
        if (p.isSneaking()) return; // shift + PPM = normalne budowanie

        PlacedGenerator g = plugin.generator(BlockKey.of(b));
        if (g == null) return;

        e.setCancelled(true);
        if (!g.owner.equals(p.getUniqueId()) && !p.hasPermission("skygenerator.bypass")) {
            p.sendMessage(plugin.msg("not-owner"));
            return;
        }
        plugin.gui().openStorage(p, g);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        e.blockList().removeIf(b -> plugin.generator(BlockKey.of(b)) != null);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        e.blockList().removeIf(b -> plugin.generator(BlockKey.of(b)) != null);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent e) {
        for (Block b : e.getBlocks()) {
            if (plugin.generator(BlockKey.of(b)) != null) {
                e.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent e) {
        for (Block b : e.getBlocks()) {
            if (plugin.generator(BlockKey.of(b)) != null) {
                e.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent e) {
        if (plugin.generator(BlockKey.of(e.getBlock())) != null) {
            e.setCancelled(true);
        }
    }
}
