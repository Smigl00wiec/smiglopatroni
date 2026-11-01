package pl.smiglo.patroni;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class PatronManager {

    private final SmigloPatroni plugin;
    private final Random rnd = new Random();
    private final Map<String, Patron> patrons = new LinkedHashMap<>();
    private final Map<UUID, String> owned = new HashMap<>();
    private final Map<UUID, Long> lastRoll = new HashMap<>();
    private File dataFile;
    private FileConfiguration data = new YamlConfiguration();
    private ItemStack currency;

    public PatronManager(SmigloPatroni plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        patrons.clear();
        ConfigurationSection ps = plugin.getConfig().getConfigurationSection("patrons");
        if (ps != null) {
            for (String id : ps.getKeys(false)) {
                ConfigurationSection s = ps.getConfigurationSection(id);
                if (s == null) continue;
                List<String> lore = s.getStringList("lore");
                patrons.put(id.toLowerCase(), new Patron(
                        id.toLowerCase(),
                        s.getInt("slot", 28),
                        s.getInt("weight", 1),
                        s.getString("name", id),
                        s.getString("head.owner", ""),
                        s.getString("head.texture", ""),
                        s.getString("god-desc", ""),
                        lore));
            }
        }

        dataFile = new File(plugin.getDataFolder(), "data.yml");
        data = dataFile.exists() ? YamlConfiguration.loadConfiguration(dataFile) : new YamlConfiguration();
        owned.clear();
        lastRoll.clear();
        ConfigurationSection pl = data.getConfigurationSection("players");
        if (pl != null) {
            for (String k : pl.getKeys(false)) {
                try {
                    UUID u = UUID.fromString(k);
                    String pid = pl.getString(k + ".patron");
                    if (pid != null) owned.put(u, pid.toLowerCase());
                    lastRoll.put(u, pl.getLong(k + ".last-roll", 0L));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        currency = data.getItemStack("currency");
    }

    public FileConfiguration data() {
        return data;
    }

    public synchronized void saveData() {
        try {
            data.set("currency", currency);
            for (Map.Entry<UUID, String> e : owned.entrySet()) {
                data.set("players." + e.getKey() + ".patron", e.getValue());
            }
            for (Map.Entry<UUID, Long> e : lastRoll.entrySet()) {
                data.set("players." + e.getKey() + ".last-roll", e.getValue());
            }
            data.save(dataFile);
        } catch (IOException ex) {
            plugin.getLogger().warning("Nie udalo sie zapisac data.yml: " + ex.getMessage());
        }
    }

    public Collection<Patron> all() {
        return patrons.values();
    }

    public Patron get(String id) {
        return id == null ? null : patrons.get(id.toLowerCase());
    }

    public String patronId(UUID u) {
        return owned.get(u);
    }

    public Patron current(UUID u) {
        return get(owned.get(u));
    }

    public void setPatron(UUID u, String id) {
        if (id == null) {
            owned.remove(u);
            data.set("players." + u + ".patron", null);
        } else {
            owned.put(u, id.toLowerCase());
        }
        saveData();
    }

    public void resetFree(UUID u) {
        lastRoll.put(u, 0L);
        saveData();
    }

    // ---- darmowe losowanie ----

    private long freeMs() {
        return plugin.getConfig().getLong("roll.free-days", 3) * 86_400_000L;
    }

    public long freeRemaining(UUID u) {
        long last = lastRoll.getOrDefault(u, 0L);
        return Math.max(0, last + freeMs() - System.currentTimeMillis());
    }

    public boolean isFreeAvailable(UUID u) {
        return freeRemaining(u) <= 0;
    }

    // ---- waluta ----

    public int cost() {
        return Math.max(1, plugin.getConfig().getInt("roll.cost", 48));
    }

    public ItemStack currency() {
        return currency;
    }

    public void setCurrency(ItemStack item) {
        if (item == null) {
            currency = null;
        } else {
            currency = item.clone();
            currency.setAmount(1);
        }
        saveData();
    }

    public int countCurrency(Player p) {
        if (currency == null) return 0;
        int n = 0;
        for (ItemStack it : p.getInventory().getStorageContents()) {
            if (it != null && it.isSimilar(currency)) n += it.getAmount();
        }
        return n;
    }

    public void takeCurrency(Player p, int amount) {
        PlayerInventory inv = p.getInventory();
        ItemStack[] contents = inv.getStorageContents();
        int left = amount;
        for (int i = 0; i < contents.length && left > 0; i++) {
            ItemStack it = contents[i];
            if (it == null || !it.isSimilar(currency)) continue;
            int take = Math.min(left, it.getAmount());
            it.setAmount(it.getAmount() - take);
            if (it.getAmount() <= 0) contents[i] = null;
            left -= take;
        }
        inv.setStorageContents(contents);
    }

    // ---- losowanie ----

    public Patron pickRandom() {
        int total = 0;
        for (Patron p : patrons.values()) total += p.weight;
        if (total <= 0) return null;
        int r = rnd.nextInt(total);
        for (Patron p : patrons.values()) {
            r -= p.weight;
            if (r < 0) return p;
        }
        return patrons.values().iterator().next();
    }

    public void tryRoll(Player p) {
        UUID id = p.getUniqueId();
        boolean free = isFreeAvailable(id);
        if (!free) {
            if (currency == null) {
                p.sendMessage(plugin.msg("no-currency"));
                plugin.sound(p, "error");
                return;
            }
            int cost = cost();
            if (countCurrency(p) < cost) {
                p.sendMessage(plugin.msg("not-enough",
                        "time", Text.duration(freeRemaining(id)),
                        "cost", cost,
                        "currency", plugin.getConfig().getString("roll.currency-name", "")));
                plugin.sound(p, "error");
                return;
            }
            takeCurrency(p, cost);
        } else {
            lastRoll.put(id, System.currentTimeMillis());
        }

        Patron picked = pickRandom();
        if (picked == null) return;
        owned.put(id, picked.id);
        saveData();

        p.sendMessage(plugin.msg("rolled", "patron", picked.name));
        plugin.sound(p, "roll");
        plugin.bonuses().sync(p);
        plugin.gui().refresh(p);
    }
}
