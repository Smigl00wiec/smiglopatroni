package pl.smiglo.patroni;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AdminCommand implements TabExecutor {

    private final SmigloPatroni plugin;

    public AdminCommand(SmigloPatroni plugin) {
        this.plugin = plugin;
    }

    private OfflinePlayer findPlayer(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online;
        OfflinePlayer off = Bukkit.getOfflinePlayer(name);
        return off.hasPlayedBefore() ? off : null;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("smiglopatroni.admin")) {
            sender.sendMessage(plugin.msg("no-permission"));
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(plugin.msg("admin-usage"));
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "currency": {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage(plugin.msg("player-only"));
                    return true;
                }
                ItemStack hand = p.getInventory().getItemInMainHand();
                if (hand == null || hand.getType().isAir()) {
                    p.sendMessage(plugin.msg("currency-hold"));
                    return true;
                }
                plugin.patrons().setCurrency(hand);
                p.sendMessage(plugin.msg("currency-set"));
                return true;
            }
            case "wylosuj": {
                if (plugin.god().isDrawing()) {
                    sender.sendMessage(plugin.msg("draw-busy"));
                    return true;
                }
                String forced = args.length > 1 ? args[1] : null;
                if (forced != null && plugin.patrons().get(forced) == null) {
                    sender.sendMessage(plugin.msg("unknown-patron"));
                    return true;
                }
                plugin.god().manualDraw(forced);
                return true;
            }
            case "set": {
                if (args.length < 3) {
                    sender.sendMessage(plugin.msg("admin-usage"));
                    return true;
                }
                OfflinePlayer target = findPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage(plugin.msg("player-not-found"));
                    return true;
                }
                String id = args[2].toLowerCase(Locale.ROOT);
                if (id.equals("none") || id.equals("brak")) {
                    plugin.patrons().setPatron(target.getUniqueId(), null);
                    sender.sendMessage(plugin.msg("set-done", "patron", "&7brak", "player", args[1]));
                } else {
                    Patron pt = plugin.patrons().get(id);
                    if (pt == null) {
                        sender.sendMessage(plugin.msg("unknown-patron"));
                        return true;
                    }
                    plugin.patrons().setPatron(target.getUniqueId(), pt.id);
                    sender.sendMessage(plugin.msg("set-done", "patron", pt.name, "player", args[1]));
                }
                Player online = target.getPlayer();
                if (online != null) plugin.bonuses().sync(online);
                return true;
            }
            case "resetcd": {
                if (args.length < 2) {
                    sender.sendMessage(plugin.msg("admin-usage"));
                    return true;
                }
                OfflinePlayer target = findPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage(plugin.msg("player-not-found"));
                    return true;
                }
                plugin.patrons().resetFree(target.getUniqueId());
                sender.sendMessage(plugin.msg("reset-done", "player", args[1]));
                return true;
            }
            case "reload": {
                plugin.reloadAll();
                sender.sendMessage(plugin.msg("reloaded"));
                return true;
            }
            default:
                sender.sendMessage(plugin.msg("admin-usage"));
                return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (!sender.hasPermission("smiglopatroni.admin")) return out;
        if (args.length == 1) {
            for (String s : new String[]{"currency", "wylosuj", "set", "resetcd", "reload"}) {
                if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(s);
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("wylosuj")) {
            for (Patron p : plugin.patrons().all()) out.add(p.id);
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("set") || args[0].equalsIgnoreCase("resetcd"))) {
            for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
        } else if (args.length == 3 && args[0].equalsIgnoreCase("set")) {
            for (Patron p : plugin.patrons().all()) out.add(p.id);
            out.add("none");
        }
        return out;
    }
}
