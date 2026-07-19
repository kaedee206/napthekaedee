package com.kaedee.napthe.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class NapTheTabCompleter implements TabCompleter {

    private static final List<String> AMOUNTS = Arrays.asList(
            "10k", "20k", "50k", "100k", "200k", "500k"
    );

    private static final List<String> TELCOS = Arrays.asList(
            "VIETTEL", "MOBIFONE", "VINAPHONE", "VNMOBI", "ZING"
    );

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // /napthe <subcommand>
            List<String> subCommands = new ArrayList<>(Arrays.asList("card", "qr", "history"));
            if (sender.hasPermission("napthe.admin")) {
                subCommands.add("adhistory");
                subCommands.add("reload");
            }
            StringUtil.copyPartialMatches(args[0], subCommands, completions);

        } else if (args.length == 2) {
            String sub = args[0].toLowerCase();

            switch (sub) {
                case "card":
                case "qr":
                    // /napthe card <amount> hoặc /napthe qr <amount>
                    StringUtil.copyPartialMatches(args[1], AMOUNTS, completions);
                    break;
                case "adhistory":
                    // /napthe adhistory [player] [page] — chỉ admin
                    if (sender.hasPermission("napthe.admin")) {
                        List<String> playerNames = new ArrayList<>();
                        for (Player p : Bukkit.getOnlinePlayers()) {
                            playerNames.add(p.getName());
                        }
                        playerNames.add("all");
                        playerNames.add("1");
                        playerNames.add("2");
                        StringUtil.copyPartialMatches(args[1], playerNames, completions);
                    }
                    break;
                // "history" không cần thêm args
            }

        } else if (args.length == 3 && args[0].equalsIgnoreCase("card")) {
            // /napthe card <amount> <telco>
            StringUtil.copyPartialMatches(args[2], TELCOS, completions);

        } else if (args.length == 4 && args[0].equalsIgnoreCase("card")) {
            // /napthe card <amount> <telco> <code>
            completions.add("<mã_thẻ>");

        } else if (args.length == 5 && args[0].equalsIgnoreCase("card")) {
            // /napthe card <amount> <telco> <code> <serial>
            completions.add("<số_seri>");
        }

        Collections.sort(completions);
        return completions;
    }
}
