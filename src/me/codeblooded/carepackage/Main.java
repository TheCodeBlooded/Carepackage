package me.codeblooded.carepackage;

import net.milkbowl.vault.chat.Chat;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class Main extends JavaPlugin implements Listener {

    protected Random random = new Random();

    protected Economy economy = null;

    protected List<Location> locations = new ArrayList<>();

    protected HashMap<ItemStack, Integer> items = new HashMap<>();

    @Override
    public void onEnable() {
        if (!(setupEconomy())) {
            Bukkit.getLogger().severe(String.format("[%s] - Disabled due to no Vault dependency found.", getDescription().getName()));

            getServer().getPluginManager().disablePlugin(this);

            return;
        }

        saveDefaultConfig();

        for(String data : getConfig().getConfigurationSection("Items").getKeys(false)) {
            String path = "Items." + data;

            ItemStack item = new ItemStack(Material.valueOf(getConfig().getString(path + ".Material").toUpperCase()), getConfig().getInt(path + ".Amount"));
            ItemMeta im = item.getItemMeta();

            if(getConfig().contains(path + ".Name")) {
                im.setDisplayName(ChatColor.translateAlternateColorCodes('&', getConfig().getString(path + ".Name")));
            }

            if(getConfig().contains(path + ".Lore")) {
                im.setLore(colouriseList(getConfig().getStringList(path + ".Lore")));
            }

            if(getConfig().contains(path + ".Data")) {
                item.setDurability((short) getConfig().getInt(path + ".Data"));
            }
            item.setItemMeta(im);

            items.put(item, getConfig().getInt(path + ".Chance"));
        }

        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        for(Location location : locations) {
            location.getBlock().setType(Material.AIR);

            location.getBlock().getState().update();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if(!(sender instanceof Player))
            return false;

        Player player = (Player) sender;

        if(command.getName().equalsIgnoreCase("carepackage")) {
            if(!(player.hasPermission("carepackage.command"))) {
                player.sendMessage(ChatColor.RED + "You do not have access to this command.");
            } else {
                if(economy.getBalance(player) < getConfig().getInt("Cost")) {
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', getConfig().getString("TooLittleMoney")));

                    return false;
                }

                EconomyResponse economyResponse = economy.withdrawPlayer(player, getConfig().getInt("Cost"));

                if(economyResponse.transactionSuccess()) {
                    FallingBlock fallingBlock = player.getLocation().getWorld().spawnFallingBlock(player.getLocation().add(0, 10, 0), Material.WOOD, (byte) 0);

                    fallingBlock.setHurtEntities(false);

                    fallingBlock.setDropItem(false);

                    fallingBlock.setMetadata("Carepackage", new FixedMetadataValue(this, true));

                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', getConfig().getString("Message")));
                } else {
                    sender.sendMessage(String.format("An error occurred: %s", economyResponse.errorMessage));
                }
            }
        }
        return false;
    }

    @EventHandler
    public void onBlockChange(EntityChangeBlockEvent e) {
        if(e.getEntity() instanceof FallingBlock) {
            if(e.getEntity().hasMetadata("Carepackage")) {
                e.setCancelled(true);

                e.getBlock().setType(Material.CHEST);

                Chest chest = (Chest) e.getBlock().getState();

                for(ItemStack item : getRandomItems(getConfig().getInt("ItemsPerPackage")))
                    chest.getBlockInventory().setItem(random.nextInt(26), item);

                chest.update();

                new BukkitRunnable() {

                    Location location = e.getBlock().getLocation();

                    @Override
                    public void run() {
                        location.getBlock().setType(Material.AIR);

                        location.getBlock().getState().update();
                    }
                }.runTaskLater(this, ((20 * 60) * 5));
            }
        }
    }

    protected List<ItemStack> getRandomItems(int amount) {
        List<ItemStack> items = new ArrayList<>();

        for(int i = 0; i < amount; i++) {
            int chance = random.nextInt(99) + 1;

            List<ItemStack> itemsWithChance = new ArrayList<>();

            for(ItemStack item : this.items.keySet()) {
                if(this.items.get(item) <= chance) {
                    itemsWithChance.add(item);
                }
            }
            Collections.shuffle(itemsWithChance);

            if(itemsWithChance.isEmpty()) {
                i--;

                continue;
            }

            items.add(itemsWithChance.get(0));
        }
        return items;
    }

    protected List<String> colouriseList(List<String> list) {
        List<String> lore = new ArrayList<>();

        for(String s : list) {
            lore.add(ChatColor.translateAlternateColorCodes('&', s));
        }
        return lore;
    }

    protected boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null)
            return false;

        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);

        if (rsp == null)
            return false;

        economy = rsp.getProvider();

        return economy != null;
    }
}
