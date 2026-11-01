package pl.smiglo.patroni;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class SmigloPatroni extends JavaPlugin {

    private HeadFactory heads;
    private PatronManager patrons;
    private GodManager god;
    private BonusService bonuses;
    private PatronGui gui;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        heads = new HeadFactory(this);
        patrons = new PatronManager(this);
        god = new GodManager(this);
        bonuses = new BonusService(this);
        gui = new PatronGui(this);

        patrons.reload();
        heads.preload();

        getServer().getPluginManager().registerEvents(gui, this);
        getServer().getPluginManager().registerEvents(bonuses, this);
        getServer().getPluginManager().registerEvents(god, this);

        PluginCommand pc = getCommand("patron");
        if (pc != null) {
            pc.setExecutor(new PatronCommand(this));
        }
        PluginCommand ac = getCommand("apatroni");
        if (ac != null) {
            AdminCommand admin = new AdminCommand(this);
            ac.setExecutor(admin);
            ac.setTabCompleter(admin);
        }

        god.start();
        bonuses.start();

        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            boolean ok = new PatronExpansion(this).register();
            if (!ok) {
                getLogger().warning("Nie udalo sie zarejestrowac placeholderow. Identyfikator '"
                        + getConfig().getString("settings.identifier", "smiglo")
                        + "' jest prawdopodobnie zajety przez inny plugin - zmien 'settings.identifier'.");
            }
        }
    }

    @Override
    public void onDisable() {
        if (god != null) god.stop();
        if (bonuses != null) bonuses.stop();
        if (patrons != null) patrons.saveData();
    }

    public void reloadAll() {
        reloadConfig();
        patrons.reload();
        heads.preload();
        god.reload();
    }

    public HeadFactory heads() { return heads; }
    public PatronManager patrons() { return patrons; }
    public GodManager god() { return god; }
    public BonusService bonuses() { return bonuses; }
    public PatronGui gui() { return gui; }

    /** Wiadomosc z configu (messages.<key>) z podmianka {klucz} -> wartosc. */
    public Component msg(String key, Object... kv) {
        return Text.parse(fmt(getConfig().getString("messages." + key, "&c[brak wiadomosci: " + key + "]"), kv));
    }

    public String fmt(String raw, Object... kv) {
        String s = raw.replace("{prefix}", getConfig().getString("messages.prefix", ""));
        for (int i = 0; i + 1 < kv.length; i += 2) {
            s = s.replace("{" + kv[i] + "}", String.valueOf(kv[i + 1]));
        }
        return s;
    }

    public void sound(Player p, String name) {
        sound(p, name, 1f, 1f);
    }

    public void sound(Player p, String name, float volume, float pitch) {
        String key = getConfig().getString("sounds." + name, "");
        if (key == null || key.isBlank()) return;
        try {
            p.playSound(Sound.sound(Key.key(key.toLowerCase()), Sound.Source.MASTER, volume, pitch));
        } catch (Exception ignored) {
        }
    }
}
