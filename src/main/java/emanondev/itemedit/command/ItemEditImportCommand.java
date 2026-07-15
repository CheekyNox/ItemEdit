package emanondev.itemedit.command;

import emanondev.itemedit.ItemEdit;
import emanondev.itemedit.Util;
import emanondev.itemedit.compability.Hooks;
import emanondev.itemedit.compability.NexoItemProvider;
import emanondev.itemedit.utility.CompleteUtility;
import emanondev.itemedit.utility.ItemUtils;
import lombok.extern.slf4j.Slf4j;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.jetbrains.annotations.NotNull;
import org.yaml.snakeyaml.external.biz.base64Coder.Base64Coder;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

@Slf4j
public class ItemEditImportCommand implements TabExecutor {

    private final ItemEdit plugin;
    private final String permission;

    public ItemEditImportCommand() {
        this.plugin = ItemEdit.get();
        this.permission = "itemedit.itemeditimport";
    }

    private static ItemStack fromBase64(String data) throws IOException {
        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64Coder.decodeLines(data));
            BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);
            ItemStack item = (ItemStack) dataInput.readObject();
            dataInput.close();
            return item;
        } catch (ClassNotFoundException e) {
            throw new IOException("Unable to decode class type.", e);
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (args.length == 1) {
            return CompleteUtility.complete(args[0], Arrays.asList("itemeditor", "nexo"));
        }
        return Collections.emptyList();
    }

    public void sendPermissionLackMessage(@NotNull String permission, CommandSender sender) {
        Util.sendMessage(sender, plugin.getLanguageConfig(sender).loadMessage("lack-permission", "&cYou lack of permission %permission%",
                sender instanceof Player ? (Player) sender : null, true
                , "%permission%",
                permission));
    }

    @Override
    public boolean onCommand(CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!sender.hasPermission(permission)) {
            sendPermissionLackMessage(permission, sender);
            return true;
        }


        if (args.length == 0) {
            Util.sendMessage(sender, String.join("\n", loadImportHelp(sender)));
            return true;
        }
        switch (args[0].toLowerCase(Locale.ENGLISH)) {
            case "nexo": {
                importNexo(sender, args);
                return true;
            }
            case "itemeditor": {
                File[] files = new File("plugins" + File.separator + "ItemEditor" + File.separator + "items").listFiles();
                if (files != null
                        && files.length != 0) {
                    List<String> importedIds = new ArrayList<>();
                    int max = files.length;
                    for (File file : files) {
                        String name = file.getName().replace(".yml", "");
                        try {
                            ItemEdit.get().getServerStorage().validateID(name);
                        } catch (Exception e) {
                            Util.sendMessage(sender, String.join("\n", plugin.getLanguageConfig(sender).loadMultiMessage(
                                    "itemeditimport.itemeditor.invalid-id", new ArrayList<>(), null, true, "%id%", name)));
                            continue;
                        }
                        if (ItemEdit.get().getServerStorage().getItem(name) != null) {
                            Util.sendMessage(sender, String.join("\n", plugin.getLanguageConfig(sender).loadMultiMessage(
                                    "itemeditimport.itemeditor.already-used-id", new ArrayList<>(), null, true,
                                    "%id%", name)));
                            continue;
                        }

                        try {
                            ItemStack item = fromBase64(YamlConfiguration.loadConfiguration(file).getString("Item"));
                            ItemEdit.get().getServerStorage().setItem(name, item);
                            importedIds.add(name);
                        } catch (Exception e) {
                            Util.sendMessage(sender, String.join("\n", plugin.getLanguageConfig(sender).loadMultiMessage(
                                    "itemeditimport.itemeditor.unable-to-get-item", new ArrayList<>(), null, true,
                                    "%id%", name)));
                            log.warn(e.getMessage(), e);
                            continue;
                        }

                    }
                    if (importedIds.isEmpty()) {
                        Util.sendMessage(sender, String.join("\n", plugin.getLanguageConfig(sender).loadMultiMessage(
                                "itemeditimport.itemeditor.import-unsuccess", new ArrayList<>(), null, true,
                                "%ids%", String.join(", ", importedIds),
                                "%max%", String.valueOf(max), "%done%", String.valueOf(importedIds.size()))));
                    } else {
                        Util.sendMessage(sender, String.join("\n", plugin.getLanguageConfig(sender).loadMultiMessage(
                                "itemeditimport.itemeditor.import-success", new ArrayList<>(), null, true,
                                "%ids%", String.join(", ", importedIds)
                                , "%max%", String.valueOf(max),
                                "%done%", String.valueOf(importedIds.size()))));
                    }

                } else {
                    Util.sendMessage(sender, String.join("\n", plugin.getLanguageConfig(sender).loadMultiMessage(
                            "itemeditimport.itemeditor.import-empty", new ArrayList<>())));
                }
                return true;
            }
            default:
                break;
        }
        Util.sendMessage(sender, String.join("\n", loadImportHelp(sender)));
        return true;
    }

    private List<String> loadImportHelp(CommandSender sender) {
        List<String> help = new ArrayList<>(plugin.getLanguageConfig(sender).loadMultiMessage(
                "itemeditimport.help", Collections.singletonList("&a/itemeditimport ItemEditor &2- import items from ItemEditor plugin")));
        boolean hasNexo = false;
        for (String line : help) {
            if (line.toLowerCase(Locale.ENGLISH).contains("nexo")) {
                hasNexo = true;
                break;
            }
        }
        if (!hasNexo) {
            help.add("&a/itemeditimport Nexo <nexoItemId> [serverItemId] &2- import item from Nexo plugin");
        }
        return help;
    }

    private void importNexo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sendNexoMessage(sender, "usage",
                    Collections.singletonList("&4[&cItemEdit&4] &cUsage: &6/itemeditimport nexo <nexoItemId> [serverItemId]"));
            return;
        }
        if (!Hooks.isNexoEnabled()) {
            sendNexoMessage(sender, "not-enabled",
                    Collections.singletonList("&4[&cItemEdit&4] &cNexo plugin is not enabled"));
            return;
        }

        String nexoId = args[1];
        String serverItemId = args.length >= 3 ? args[2] : nexoId;
        try {
            ItemEdit.get().getServerStorage().validateID(serverItemId);
        } catch (Exception e) {
            sendNexoMessage(sender, "invalid-id",
                    Collections.singletonList("&4[&cItemEdit&4] &6%id% &cis not an acceptable server item id"),
                    "%id%", serverItemId);
            return;
        }
        if (ItemEdit.get().getServerStorage().getItem(serverItemId) != null) {
            sendNexoMessage(sender, "already-used-id",
                    Collections.singletonList("&4[&cItemEdit&4] &6%id% &cis already used"),
                    "%id%", serverItemId);
            return;
        }

        ItemStack item = NexoItemProvider.getItem(nexoId);
        if (ItemUtils.isAirOrNull(item)) {
            sendNexoMessage(sender, "unable-to-get-item",
                    Collections.singletonList("&4[&cItemEdit&4] &cUnable to load Nexo item &6%id%&c. If Nexo is still loading items, try again in a few seconds."),
                    "%id%", nexoId);
            return;
        }

        try {
            ItemEdit.get().getServerStorage().setItem(serverItemId, item);
            String itemName = NexoItemProvider.getItemName(nexoId);
            if (itemName != null && !itemName.isEmpty()) {
                ItemEdit.get().getServerStorage().setNick(serverItemId, itemName);
            }
            sendNexoMessage(sender, "import-success",
                    Collections.singletonList("&9[&fItemEdit&9] &aImported Nexo item &e%nexo_id% &aas server item &e%id%"),
                    "%nexo_id%", nexoId,
                    "%id%", serverItemId);
        } catch (Exception e) {
            sendNexoMessage(sender, "unable-to-save-item",
                    Collections.singletonList("&4[&cItemEdit&4] &cUnable to save Nexo item &6%id%"),
                    "%id%", nexoId);
            log.warn(e.getMessage(), e);
        }
    }

    private void sendNexoMessage(CommandSender sender, String path, List<String> defaults, String... holders) {
        Util.sendMessage(sender, String.join("\n", plugin.getLanguageConfig(sender).loadMultiMessage(
                "itemeditimport.nexo." + path, defaults, sender instanceof Player ? (Player) sender : null, true, holders)));
    }

}
