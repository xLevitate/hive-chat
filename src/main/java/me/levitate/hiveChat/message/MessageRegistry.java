package me.levitate.hiveChat.message;

import me.levitate.hiveChat.HiveChat;
import me.levitate.hiveChat.placeholder.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for pre-defined messages
 */
public class MessageRegistry {
    
    private final Map<String, String> messages = new ConcurrentHashMap<>();
    
    /**
     * Register a message template
     * @param key The key to identify this message
     * @param message The message content
     * @return This registry for chaining
     */
    public MessageRegistry register(String key, String message) {
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("Message key cannot be null or empty");
        }
        messages.put(key, message != null ? message : "");
        // Pre-cache the message parsing
        HiveChat.saveMessage(key, message);
        return this;
    }
    
    /**
     * Check if a message with the given key exists
     * @param key The message key
     * @return true if the message exists, false otherwise
     */
    public boolean hasMessage(String key) {
        return messages.containsKey(key);
    }
    
    /**
     * Get a message template by key
     * @param key The message key
     * @return The message content or null if not found
     */
    public String getMessage(String key) {
        return messages.get(key);
    }
    
    /**
     * Remove a message template
     * @param key The message key
     * @return This registry for chaining
     */
    public MessageRegistry unregister(String key) {
        messages.remove(key);
        return this;
    }
    
    /**
     * Get all registered message keys
     * @return An unmodifiable set of message keys
     */
    public Set<String> getKeys() {
        return Collections.unmodifiableSet(messages.keySet());
    }
    
    /**
     * Send a registered message to a player
     * @param key The message key
     * @param player The player to send to
     * @param placeholders Optional placeholders
     */
    public void send(String key, Player player, Placeholder... placeholders) {
        if (player == null || !player.isOnline()) return;
        
        String message = messages.get(key);
        if (message != null) {
            HiveChat.sendSaved(key, player, placeholders);
        }
    }
    
    /**
     * Send a registered message to a command sender
     * @param key The message key
     * @param sender The command sender
     * @param placeholders Optional placeholders
     */
    public void send(String key, CommandSender sender, Placeholder... placeholders) {
        if (sender == null) return;
        
        String message = messages.get(key);
        if (message != null) {
            if (sender instanceof Player player) {
                HiveChat.sendSaved(key, player, placeholders);
            } else {
                HiveChat.send(sender, message, placeholders);
            }
        }
    }
    
    /**
     * Import messages from a map
     * @param messagesMap Map of message keys to message content
     * @return This registry for chaining
     */
    public MessageRegistry importMessages(Map<String, String> messagesMap) {
        if (messagesMap == null || messagesMap.isEmpty()) return this;
        
        messagesMap.forEach(this::register);
        return this;
    }
    
    /**
     * Clear all registered messages
     * @return This registry for chaining
     */
    public MessageRegistry clearMessages() {
        messages.clear();
        return this;
    }
    
    /**
     * Get the number of registered messages
     * @return Count of registered messages
     */
    public int size() {
        return messages.size();
    }
} 