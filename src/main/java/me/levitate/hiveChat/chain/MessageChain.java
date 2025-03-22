package me.levitate.hiveChat.chain;

import me.levitate.hiveChat.message.ActionBar;
import me.levitate.hiveChat.message.BossBarComponent;
import me.levitate.hiveChat.message.MessageComponent;
import me.levitate.hiveChat.message.TitleComponent;
import me.levitate.hiveChat.placeholder.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class MessageChain {
    private final List<MessageComponent> components = new ArrayList<>();

    public MessageChain message(String content) {
        MessageComponent component = new MessageComponent()
                .setContent(content);
        components.add(component);
        return this;
    }

    public MessageChain withSound(Sound sound) {
        return withSound(sound, 1.0f, 1.0f);
    }

    public MessageChain withSound(Sound sound, float volume, float pitch) {
        MessageComponent component = new MessageComponent()
                .setSound(sound)
                .setVolume(volume)
                .setPitch(pitch);
        components.add(component);
        return this;
    }

    public MessageChain withActionBar(String content) {
        return withActionBar(content, 60);
    }

    public MessageChain withActionBar(String content, int durationTicks) {
        ActionBar actionBar = new ActionBar()
                .setContent(content)
                .setDuration(durationTicks);
        MessageComponent component = new MessageComponent()
                .setActionBar(actionBar);
        components.add(component);
        return this;
    }

    public MessageChain withBossBar(String content, BarColor color, BarStyle style) {
        return withBossBar(content, color, style, 1.0, 600);
    }

    public MessageChain withBossBar(String content, BarColor color, BarStyle style,
                                    double progress, int durationTicks) {
        BossBarComponent bossBar = new BossBarComponent()
                .setContent(content)
                .setColor(color)
                .setStyle(style)
                .setProgress(progress)
                .setDuration(durationTicks);
        MessageComponent component = new MessageComponent()
                .setBossBar(bossBar);
        components.add(component);
        return this;
    }

    public MessageChain withTitle(String title) {
        return withTitle(title, "", 10, 70, 20);
    }

    public MessageChain withTitle(String title, String subtitle) {
        return withTitle(title, subtitle, 10, 70, 20);
    }

    public MessageChain withTitle(String title, String subtitle,
                                  int fadeIn, int stay, int fadeOut) {
        TitleComponent titleComponent = new TitleComponent()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setTimes(fadeIn, stay, fadeOut);
        MessageComponent component = new MessageComponent()
                .setTitle(titleComponent);
        components.add(component);
        return this;
    }

    public void send(Player player, Placeholder... placeholders) {
        if (player == null || !player.isOnline()) return;

        for (MessageComponent component : components) {
            component.send(player, placeholders);
        }
    }

    public void broadcast(Placeholder... placeholders) {
        Bukkit.getOnlinePlayers().forEach(player -> send(player, placeholders));
    }

    public void broadcast(int radius, Location center, Placeholder... placeholders) {
        if (center == null || center.getWorld() == null) return;

        center.getWorld().getPlayers().stream()
                .filter(p -> p.getLocation().distance(center) <= radius)
                .forEach(player -> send(player, placeholders));
    }
}