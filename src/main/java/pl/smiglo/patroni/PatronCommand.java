package pl.smiglo.patroni;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class PatronCommand implements CommandExecutor {

    private final SmigloPatroni plugin;

    public PatronCommand(SmigloPatroni plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(plugin.msg("player-only"));
            return true;
        }
        plugin.gui().open(p);
        return true;
    }
}
