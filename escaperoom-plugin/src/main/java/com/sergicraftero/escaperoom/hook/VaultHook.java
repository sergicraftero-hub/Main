package com.sergicraftero.escaperoom.hook;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.Method;
import java.util.logging.Logger;

/** Optional Vault economy access through reflection (no compile-time dependency on Vault). */
public final class VaultHook {

    private final Object economy;
    private final Method deposit;

    private VaultHook(Object economy, Method deposit) {
        this.economy = economy;
        this.deposit = deposit;
    }

    public static VaultHook tryHook(Logger logger) {
        if (!Bukkit.getPluginManager().isPluginEnabled("Vault")) {
            return null;
        }
        try {
            Class<?> economyClass = Class.forName("net.milkbowl.vault.economy.Economy");
            RegisteredServiceProvider<?> rsp = Bukkit.getServicesManager().getRegistration(economyClass);
            if (rsp == null) {
                return null;
            }
            Method deposit = economyClass.getMethod("depositPlayer", OfflinePlayer.class, double.class);
            logger.info("Hooked into Vault economy.");
            return new VaultHook(rsp.getProvider(), deposit);
        } catch (Exception e) {
            logger.warning("Vault found but economy could not be hooked: " + e.getMessage());
            return null;
        }
    }

    public boolean deposit(OfflinePlayer player, double amount) {
        try {
            deposit.invoke(economy, player, amount);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
