package me.levitate.hiveChat.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ColorUtil {
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final Pattern MINI_MESSAGE_PATTERN = Pattern.compile("<[^>]+>");
    
    public static String translateHexColors(String message) {
        if (message == null) return null;
        
        Matcher matcher = HEX_PATTERN.matcher(message);
        StringBuilder buffer = new StringBuilder();
        
        while (matcher.find()) {
            String color = matcher.group(1);
            matcher.appendReplacement(buffer, net.kyori.adventure.text.format.TextColor
                .fromHexString("#" + color).toString());
        }
        
        matcher.appendTail(buffer);
        return buffer.toString();
    }
    
    public static boolean containsMiniMessage(String message) {
        if (message == null) return false;
        return MINI_MESSAGE_PATTERN.matcher(message).find();
    }
    
    public static boolean containsHexColors(String message) {
        if (message == null) return false;
        return HEX_PATTERN.matcher(message).find();
    }
    
    public static Component parseMessageFormats(String message) {
        if (message == null) return Component.empty();
        
        // Handle hex colors first
        String processed = translateHexColors(message);
        
        // Then use MiniMessage for the rest
        return MiniMessage.miniMessage().deserialize(processed);
    }
}