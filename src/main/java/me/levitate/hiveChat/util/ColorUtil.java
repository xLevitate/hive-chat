package me.levitate.hiveChat.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ColorUtil {
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");

    public static String translateHexColors(String message) {
        if (message == null) return null;

        Matcher matcher = HEX_PATTERN.matcher(message);
        StringBuilder buffer = new StringBuilder();

        while (matcher.find()) {
            String color = matcher.group(1);
            matcher.appendReplacement(buffer, "<#" + color + ">");
        }

        matcher.appendTail(buffer);
        return buffer.toString();
    }

    public static Component parseMessageFormats(String message) {
        if (message == null) return Component.empty();

        String processed = translateHexColors(message);
        return MiniMessage.miniMessage().deserialize(processed);
    }
}