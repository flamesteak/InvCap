package com.flamesteak.invcap;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;

public class InvCap extends JavaPlugin implements Listener {

    private final Map<Material, Integer> itemLimits = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadItemLimits();
        startInventoryCheckTask();
        Bukkit.getPluginManager().registerEvents(this, this);
    }

    private void loadItemLimits() {
        FileConfiguration config = getConfig();
        if (config.contains("item-limits")) {
            for (String key : config.getConfigurationSection("item-limits").getKeys(false)) {
                Material material = Material.matchMaterial(key.toUpperCase());
                if (material != null) {
                    itemLimits.put(material, config.getInt("item-limits." + key));
                }
            }
        }
    }

    private void startInventoryCheckTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    checkInventoryLimits(player);
                }
            }
        }.runTaskTimer(this, 20L, 20L);
    }

    private void checkInventoryLimits(Player player) {
        Map<Material, Integer> itemCounts = new HashMap<>();

        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (stack != null) {
                itemCounts.put(stack.getType(), itemCounts.getOrDefault(stack.getType(), 0) + stack.getAmount());
            }
        }

        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (offhand.getType() != Material.AIR) {
            itemCounts.put(offhand.getType(), itemCounts.getOrDefault(offhand.getType(), 0) + offhand.getAmount());
        }

        for (Map.Entry<Material, Integer> entry : itemLimits.entrySet()) {
            Material material = entry.getKey();
            int maxCount = entry.getValue();
            int currentCount = itemCounts.getOrDefault(material, 0);

            if (maxCount == 0) {
                removeExcessItems(player, material, currentCount);
            } else if (currentCount > maxCount) {
                removeExcessItems(player, material, currentCount - maxCount);
                player.sendActionBar("\u00a7c" + material.name().replace("_", " ") + " limit exceeded! Max: " + maxCount);
            }
        }
    }

    private void removeExcessItems(Player player, Material material, int excessAmount) {
        int remainingToRemove = excessAmount;

        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (remainingToRemove <= 0) break;
            if (stack != null && stack.getType() == material) {
                int toRemove = Math.min(stack.getAmount(), remainingToRemove);
                stack.setAmount(stack.getAmount() - toRemove);
                remainingToRemove -= toRemove;
                player.getWorld().dropItemNaturally(player.getLocation(), new ItemStack(material, toRemove));
            }
        }

        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (remainingToRemove > 0 && offhand.getType() == material) {
            int toRemove = Math.min(offhand.getAmount(), remainingToRemove);
            offhand.setAmount(offhand.getAmount() - toRemove);
            player.getWorld().dropItemNaturally(player.getLocation(), new ItemStack(material, toRemove));
        }
    }

    @EventHandler
    public void onItemPickup(PlayerPickupItemEvent event) {
        Player player = event.getPlayer();
        Material material = event.getItem().getItemStack().getType();
        int maxCount = itemLimits.getOrDefault(material, Integer.MAX_VALUE);
        int currentCount = 0;

        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (stack != null && stack.getType() == material) {
                currentCount += stack.getAmount();
            }
        }

        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (offhand.getType() == material) {
            currentCount += offhand.getAmount();
        }

        if (maxCount == 0 || currentCount >= maxCount) {
            event.setCancelled(true);
            player.sendActionBar("\u00a7cYou cannot pick up more " + material.name().replace("_", " ") + " (limit: " + maxCount + ")!");
        }
    }
}
