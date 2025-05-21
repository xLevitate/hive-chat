package me.levitate.hiveChat.placeholder;

import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Manager for universal placeholders that are applied to all messages
 */
public class UniversalPlaceholderManager {
    
    private static final UniversalPlaceholderManager instance = new UniversalPlaceholderManager();
    
    // Static placeholders that don't depend on a player
    private final Map<String, String> staticPlaceholders = new ConcurrentHashMap<>();
    
    // Dynamic placeholders that depend on a player
    private final Map<String, Function<Player, String>> dynamicPlaceholders = new ConcurrentHashMap<>();
    
    // Private constructor for singleton pattern
    private UniversalPlaceholderManager() {
        // Private constructor prevents direct instantiation
    }
    
    /**
     * Get the singleton instance
     * @return The manager instance
     */
    public static UniversalPlaceholderManager getInstance() {
        return instance;
    }
    
    /**
     * Add a static placeholder that will be replaced in all messages
     * @param key The placeholder key (without curly braces)
     * @param value The replacement value
     * @return This manager for chaining
     */
    public UniversalPlaceholderManager addStaticPlaceholder(String key, String value) {
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("Placeholder key cannot be null or empty");
        }
        staticPlaceholders.put(key, value != null ? value : "");
        return this;
    }
    
    /**
     * Add a dynamic placeholder that will be replaced based on the player context
     * @param key The placeholder key (without curly braces)
     * @param valueSupplier A function that accepts a Player and returns the replacement value
     * @return This manager for chaining
     */
    public UniversalPlaceholderManager addDynamicPlaceholder(String key, Function<Player, String> valueSupplier) {
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("Placeholder key cannot be null or empty");
        }
        if (valueSupplier == null) {
            throw new IllegalArgumentException("Value supplier cannot be null");
        }
        dynamicPlaceholders.put(key, valueSupplier);
        return this;
    }
    
    /**
     * Remove a universal placeholder
     * @param key The placeholder key
     * @return This manager for chaining
     */
    public UniversalPlaceholderManager removePlaceholder(String key) {
        staticPlaceholders.remove(key);
        dynamicPlaceholders.remove(key);
        return this;
    }
    
    /**
     * Clear all universal placeholders
     * @return This manager for chaining
     */
    public UniversalPlaceholderManager clearPlaceholders() {
        staticPlaceholders.clear();
        dynamicPlaceholders.clear();
        return this;
    }
    
    /**
     * Get all static placeholders as Placeholder objects
     * @return An array of Placeholder objects
     */
    public Placeholder[] getStaticPlaceholders() {
        return staticPlaceholders.entrySet().stream()
                .map(entry -> Placeholder.of(entry.getKey(), entry.getValue()))
                .toArray(Placeholder[]::new);
    }
    
    /**
     * Get all dynamic placeholders for a specific player as Placeholder objects
     * @param player The player to get placeholders for
     * @return An array of Placeholder objects
     */
    public Placeholder[] getDynamicPlaceholders(Player player) {
        if (player == null) {
            return new Placeholder[0];
        }
        
        return dynamicPlaceholders.entrySet().stream()
                .map(entry -> {
                    String value;
                    try {
                        value = entry.getValue().apply(player);
                    } catch (Exception e) {
                        value = "";
                    }
                    return Placeholder.of(entry.getKey(), value);
                })
                .toArray(Placeholder[]::new);
    }
    
    /**
     * Get all placeholders (static and dynamic) for a specific player context
     * @param player The player context (can be null for static placeholders only)
     * @return An array of combined Placeholder objects
     */
    public Placeholder[] getAllPlaceholders(Player player) {
        if (player == null) {
            return getStaticPlaceholders();
        }
        
        Map<String, String> allValues = new HashMap<>(staticPlaceholders);
        
        // Add dynamic placeholders
        for (Map.Entry<String, Function<Player, String>> entry : dynamicPlaceholders.entrySet()) {
            try {
                String value = entry.getValue().apply(player);
                allValues.put(entry.getKey(), value != null ? value : "");
            } catch (Exception ignored) {
                // Skip failed dynamic placeholders
            }
        }
        
        return allValues.entrySet().stream()
                .map(entry -> Placeholder.of(entry.getKey(), entry.getValue()))
                .toArray(Placeholder[]::new);
    }
    
    /**
     * Check if there are any universal placeholders registered
     * @return true if there are universal placeholders, false otherwise
     */
    public boolean hasPlaceholders() {
        return !staticPlaceholders.isEmpty() || !dynamicPlaceholders.isEmpty();
    }
} 