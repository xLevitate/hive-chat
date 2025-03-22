package me.levitate.hiveChat.message;

import me.levitate.hiveChat.HiveChat;
import me.levitate.hiveChat.placeholder.Placeholder;
import me.levitate.hiveChat.util.ColorUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;

import java.time.Duration;

public class TitleComponent {
    private String title;
    private String subtitle;
    private int fadeIn = 10;
    private int stay = 70;
    private int fadeOut = 20;

    public void show(Player player, Placeholder... placeholders) {
        if (player == null || !player.isOnline()) return;

        String processedTitle = title != null ?
                HiveChat.getParser().applyPlaceholders(title, player, placeholders) : "";
        String processedSubtitle = subtitle != null ?
                HiveChat.getParser().applyPlaceholders(subtitle, player, placeholders) : "";

        Component titleComponent = ColorUtil.parseMessageFormats(processedTitle);
        Component subtitleComponent = ColorUtil.parseMessageFormats(processedSubtitle);

        Title.Times times = Title.Times.times(
                Duration.ofMillis(fadeIn * 50L),
                Duration.ofMillis(stay * 50L),
                Duration.ofMillis(fadeOut * 50L)
        );

        player.showTitle(Title.title(titleComponent, subtitleComponent, times));
    }

    // Getters and setters with fluent interface
    public TitleComponent setTitle(String title) {
        this.title = title;
        return this;
    }

    public TitleComponent setSubtitle(String subtitle) {
        this.subtitle = subtitle;
        return this;
    }

    public TitleComponent setTimes(int fadeIn, int stay, int fadeOut) {
        this.fadeIn = fadeIn;
        this.stay = stay;
        this.fadeOut = fadeOut;
        return this;
    }
}