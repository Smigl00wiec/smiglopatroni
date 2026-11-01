package pl.smiglo.patroni;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class PatronGui implements Listener {

    public static class Holder implements InventoryHolder {
        private Inventory inventory;

        public void set(Inventory inv) {
            this.inventory = inv;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private final SmigloPatroni plugin;

    public PatronGui(SmigloPatroni plugin) {
        this.plugin = plugin;
    }

    public void open(Player p) {
        Holder holder = new Holder();
        Inventory inv = Bukkit.createInventory(holder, 54,
                Text.parse(plugin.getConfig().getString("gui.title", "&8Patroni")));
        holder.set(inv);
        populate(inv, p);
        p.openInventory(inv);
        plugin.sound(p, "gui-open");
    }

    public void refresh(Player p) {
        Inventory top = p.getOpenInventory().getTopInventory();
        if (top.getHolder() instanceof Holder) {
            populate(top, p);
        }
    }

    private Material mat(String name, Material def) {
        Material m = name == null ? null : Material.matchMaterial(name);
        return m == null ? def : m;
    }

    private ItemStack named(ItemStack item, String name, List<String> lore, boolean glow) {
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Text.item(name));
        if (lore != null && !lore.isEmpty()) {
            List<net.kyori.adventure.text.Component> l = new ArrayList<>();
            for (String line : lore) l.add(Text.item(line));
            meta.lore(l);
        }
        if (glow) {
            meta.setEnchantmentGlintOverride(true);
        }
        item.setItemMeta(meta);
        return item;
    }

    private List<String> replaceAll(List<String> in, Object... kv) {
        List<String> out = new ArrayList<>();
        for (String s : in) out.add(plugin.fmt(s, kv));
        return out;
    }

    public void populate(Inventory inv, Player p) {
        FileConfiguration c = plugin.getConfig();
        PatronManager pm = plugin.patrons();
        GodManager god = plugin.god();
        inv.clear();

        // ramka: na zmiane niebieskie / biale szkla
        Material blue = mat(c.getString("gui.glass-blue"), Material.BLUE_STAINED_GLASS_PANE);
        Material white = mat(c.getString("gui.glass-white"), Material.WHITE_STAINED_GLASS_PANE);
        for (int slot = 0; slot < 54; slot++) {
            int row = slot / 9;
            int col = slot % 9;
            if (row == 0 || row == 5 || col == 0 || col == 8) {
                Material m = ((row + col) % 2 == 0) ? blue : white;
                inv.setItem(slot, named(new ItemStack(m), " ", null, false));
            }
        }

        // ogniska i bannery
        Material campfire = mat(c.getString("gui.campfire-material"), Material.CAMPFIRE);
        for (int s : c.getIntegerList("gui.campfire-slots")) {
            if (s >= 0 && s < 54) inv.setItem(s, named(new ItemStack(campfire), " ", null, false));
        }
        Material banner = mat(c.getString("gui.banner-material"), Material.BLUE_BANNER);
        for (int s : c.getIntegerList("gui.banner-slots")) {
            if (s >= 0 && s < 54) inv.setItem(s, named(new ItemStack(banner), " ", null, false));
        }

        // kostka (losowanie)
        boolean free = pm.isFreeAvailable(p.getUniqueId());
        String timeLeft = Text.duration(pm.freeRemaining(p.getUniqueId()));
        String status = free
                ? c.getString("gui.dice.status-active", "&aAKTYWNE")
                : plugin.fmt(c.getString("gui.dice.status-inactive", "&cNIE AKTYWNE"), "time", timeLeft);
        ItemStack dice = plugin.heads().create(
                c.getString("gui.dice.head.owner", ""), c.getString("gui.dice.head.texture", ""));
        named(dice, c.getString("gui.dice.name", "&bLosowanie patrona"),
                replaceAll(c.getStringList("gui.dice.lore"),
                        "days", c.getInt("roll.free-days", 3),
                        "cost", pm.cost(),
                        "currency", c.getString("roll.currency-name", ""),
                        "free-status", status),
                false);
        inv.setItem(c.getInt("gui.dice.slot", 12), dice);

        // informacje
        Patron mine = pm.current(p.getUniqueId());
        Patron g = god.currentPatron();
        ItemStack info = new ItemStack(mat(c.getString("gui.info.material"), Material.BIRCH_SIGN));
        named(info, c.getString("gui.info.name", "&bINFORMACJE"),
                replaceAll(c.getStringList("gui.info.lore"),
                        "patron", mine != null ? mine.name : c.getString("settings.placeholder-none", "&7Brak"),
                        "free-time", free ? "teraz" : timeLeft,
                        "start", c.getString("god.start", "13:30"),
                        "end", c.getString("god.end", "22:30"),
                        "god", g != null ? g.name : "&7brak",
                        "god-time", g != null ? Text.clock(god.remaining()) : "-"),
                false);
        inv.setItem(c.getInt("gui.info.slot", 14), info);

        // patroni
        for (Patron pt : pm.all()) {
            boolean active = mine != null && mine.id.equals(pt.id);
            ItemStack head = plugin.heads().create(pt.headOwner, pt.headTexture);
            List<String> lore = new ArrayList<>(pt.lore);
            if (active) lore.addAll(c.getStringList("gui.active-lore"));
            named(head, pt.name, lore, active);
            if (pt.slot >= 0 && pt.slot < 54) inv.setItem(pt.slot, head);
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Holder)) return;
        e.setCancelled(true);
        if (e.getClickedInventory() == null || !(e.getClickedInventory().getHolder() instanceof Holder)) return;
        if (!(e.getWhoClicked() instanceof Player p)) return;
        int diceSlot = plugin.getConfig().getInt("gui.dice.slot", 12);
        if (e.getSlot() == diceSlot
                && (e.getClick() == ClickType.RIGHT || e.getClick() == ClickType.SHIFT_RIGHT)) {
            plugin.patrons().tryRoll(p);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Holder) {
            e.setCancelled(true);
        }
    }
}
