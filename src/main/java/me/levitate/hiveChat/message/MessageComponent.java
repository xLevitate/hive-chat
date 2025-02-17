package me.levitate.hiveChat.message;

import me.clip.placeholderapi.PlaceholderAPI;
import me.levitate.hiveChat.HiveChat;
import me.levitate.hiveChat.placeholder.Placeholder;
import me.levitate.hiveChat.util.ColorUtil;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

public class MessageComponent {
    private String content;
    private Sound sound;
    private float volume = 1.0f;
    private float pitch = 1.0f;
    private ActionBar actionBar;
    private BossBarComponent bossBar;
    private TitleComponent title;
    
    public void send(Player player, Placeholder... placeholders) {
        if (player == null || !player.isOnline()) return;
        
        // Process content if exists
        if (content != null) {
            String processed = applyPlaceholders(content, player, placeholders);
            player.sendMessage(ColorUtil.parseMessageFormats(processed));
        }
        
        // Play sound if exists
        if (sound != null) {
            player.playSound(player.getLocation(), sound, volume, pitch);
        }
        
        // Show action bar if exists
        if (actionBar != null) {
            actionBar.show(player, placeholders);
        }
        
        // Show boss bar if exists
        if (bossBar != null) {
            bossBar.show(player, placeholders);
        }
        
        // Show title if exists
        if (title != null) {
            title.show(player, placeholders);
        }
    }
    
    private String applyPlaceholders(String text, Player player, Placeholder... placeholders) {
        if (text == null) return "";
        
        // Apply custom placeholders
        String processed = text;
        for (Placeholder placeholder : placeholders) {
            processed = processed.replace(
                "{" + placeholder.getKey() + "}", 
                placeholder.getValue()
            );
        }
        
        // Apply PlaceholderAPI if enabled
        if (HiveChat.isPapiEnabled() && PlaceholderAPI.containsPlaceholders(processed)) {
            processed = PlaceholderAPI.setPlaceholders(player, processed);
        }
        
        return processed;
    }
    
    // Getters and setters with fluent interface
    public MessageComponent setContent(String content) {
        this.content = content;
        return this;
    }
    
    public MessageComponent setSound(Sound sound) {
        this.sound = sound;
        return this;
    }
    
    public MessageComponent setVolume(float volume) {
        this.volume = volume;
        return this;
    }
    
    public MessageComponent setPitch(float pitch) {
        this.pitch = pitch;
        return this;
    }
    
    public MessageComponent setActionBar(ActionBar actionBar) {
        this.actionBar = actionBar;
        return this;
    }
    
    public MessageComponent setBossBar(BossBarComponent bossBar) {
        this.bossBar = bossBar;
        return this;
    }
    
    public MessageComponent setTitle(TitleComponent title) {
        this.title = title;
        return this;
    }
}