package online.umbcraft.libraries.dupes;

import online.umbcraft.libraries.GoldenDupes;
import online.umbcraft.libraries.config.ConfigPath;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

public class AutocraftDupe implements Listener {

    // the amount of extra items to be given to the player
    final private Map<UUID, Integer> dupeAmnt = new TreeMap<>();

    // the BukkitScheduler task ID of the most recent dupe reset event about to be run
    final private Map<UUID, Integer> dupeTask = new TreeMap<>();

    // whether a player used the crafting autocomplete menu, or just clicked / drag clicked the item into the table
    final private Map<UUID, Boolean> clickValidity = new TreeMap<>();

    final private GoldenDupes plugin;

    public AutocraftDupe(GoldenDupes plugin) {
        this.plugin = plugin;
    }

    // prevents the player from getting an extra item if they touch the crafting table normally
    @EventHandler(priority = EventPriority.NORMAL)
    public void onClickInv(final InventoryClickEvent e) {

        clickValidity.put(e.getWhoClicked().getUniqueId(), false);
        Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {
            clickValidity.remove(e.getWhoClicked().getUniqueId());
        }, 1L);

    }

    // prevents the player from getting an extra item if they touch the crafting table normally
    @EventHandler(priority = EventPriority.NORMAL)
    public void onDragInv(final InventoryDragEvent e) {

        clickValidity.put(e.getWhoClicked().getUniqueId(), false);
        Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {
            clickValidity.remove(e.getWhoClicked().getUniqueId());
        }, 1L);

    }


    // increments the amount of duped items a player will receive, and checks to make sure they used the autocomplete
    @EventHandler(priority = EventPriority.HIGH)
    public void onCraftItem(final PrepareItemCraftEvent e) {

        final UUID player_uuid = e.getView().getPlayer().getUniqueId();


        // no extra items if the player didn't autocomplete the recipe
        if (clickValidity.containsKey(player_uuid)) {
            return;
        }


        // increment the number of extra items by 2
        final int currentAmnt = dupeAmnt.getOrDefault(player_uuid, 0);
        dupeAmnt.put(player_uuid, currentAmnt + 2);


        // prolongs the time until the extra items reset to 0 by one second
        cancelDupeClearTask(player_uuid);
        dupeTask.put(player_uuid,
                Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {
                    dupeAmnt.remove(player_uuid);
                }, 50L));


        // makes the next click(s) this game tick not give extra items; needed due to how PrepareItemCraftEvent gets triggered
        clickValidity.put(player_uuid, false);
        Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {
            clickValidity.remove(player_uuid);
        }, 0L);

    }

    // cancels an existing queued task which would set the player's extra item count to zero
    private void cancelDupeClearTask(final UUID player) {
        final int lastTask = dupeTask.getOrDefault(player, -1);
        if (Bukkit.getScheduler().isQueued(lastTask)) {
            Bukkit.getScheduler().cancelTask(lastTask);
        }
        dupeTask.remove(player);
    }


    // player extra item count resets if they leave the menu
    @EventHandler(priority = EventPriority.HIGH)
    public void onCloseInv(final InventoryCloseEvent e) {
        dupeAmnt.remove(e.getPlayer().getUniqueId());
        cancelDupeClearTask(e.getPlayer().getUniqueId());
    }


    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerKick(final PlayerKickEvent e) {
        dupeAmnt.remove(e.getPlayer().getUniqueId());
        dupeTask.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerLeave(final PlayerQuitEvent e) {
        dupeAmnt.remove(e.getPlayer().getUniqueId());
        dupeTask.remove(e.getPlayer().getUniqueId());
    }


    // gives the player the extra items once they pick up after performing the dupe
    @EventHandler(priority = EventPriority.HIGH)
    public void onItemPickup(final EntityPickupItemEvent e) {

        if (!(e.getEntity() instanceof Player))
            return;

        final Player p = (Player) e.getEntity();


        // players who didn't do the dupe are not affected
        if (!dupeAmnt.containsKey(p.getUniqueId())) {
            return;
        }


        final ItemStack stack = e.getItem().getItemStack();

        // set stacksize to 64 if vanilla, get correct size otherwise
        int stacksize = (plugin.getConfig().getBoolean(ConfigPath.AUTOCRAFT_VANILLA.path()))
                ? 64 : decideAmount(stack.getType(), p.getUniqueId());


        // stacksize of 1 is a special case; this gives an extra item to the player without affecting the existing item
        if(stacksize == 1) {
            ItemStack toAdd = stack.clone();
            toAdd.setAmount(1);
            p.getInventory().addItem(toAdd);
        }
        else {
            stack.setAmount(stacksize);
            p.getInventory().addItem(stack);
        }

        // player only dupes the first item he picks up
        dupeAmnt.remove(p.getUniqueId());
    }


    private int decideAmount(Material m, UUID uuid) {

        // defaults to the max amount of items the player can receive
        int player_earned = Math.min(
                64,
                plugin.getConfig().getInt(ConfigPath.AUTOCRAFT_MAX_ITEMS.path()));


        // if Items-Per-Click is enabled, uses the smaller between that value and the maximum
        if (plugin.getConfig().getBoolean(ConfigPath.AUTOCRAFT_IPC.path()))
            player_earned = Math.min(
                    player_earned,
                    dupeAmnt.get(uuid) * plugin.getConfig().getInt(ConfigPath.AUTOCRAFT_MULTIPLIER.path()));


        // the max stack size for unstackable items
        int nonstack_size = plugin.getConfig().getInt(ConfigPath.NON_STACK_STACKSIZE.path());

        // the max stack size for totems; defaults to the unstackable cap if set to less than 0
        int totem_size = plugin.getConfig().getInt(ConfigPath.TOTEMS_STACKSIZE.path());
        totem_size = (totem_size < 0) ? nonstack_size : totem_size;

        // the max stack size for shulkers; defaults to the unstackable cap if set to less than 0
        int shulker_size = plugin.getConfig().getInt(ConfigPath.SHULKERS_STACKSIZE.path());
        shulker_size = (shulker_size < 0) ? nonstack_size : shulker_size;




        // if the item is a totem, uses the smaller of what the player should get, and the maximum totem size
        if (m.equals(Material.TOTEM_OF_UNDYING))
            return (plugin.getConfig().getBoolean(ConfigPath.TOTEMS_DO_DUPE.path())) ?
                    Math.min(player_earned, totem_size) : 0;


        // if the item is a shulker box, uses the smaller of what the player should get, and the maximum shulker size
        if (m.name().endsWith("SHULKER_BOX"))
            return (plugin.getConfig().getBoolean(ConfigPath.SHULKERS_DO_DUPE.path())) ?
                    Math.min(player_earned, shulker_size) : 0;


        // if the item is any other nonstackable, uses the smaller of what the player should get, and the maximum stackable size
        if (m.getMaxStackSize() != 64)
            return (plugin.getConfig().getBoolean(ConfigPath.NON_STACK_DO_DUPE.path())) ?
                    Math.min(player_earned, nonstack_size) : 0;


        // if the item is stackable to 64, returns the regular amount earned by the player
        return player_earned;
    }


}