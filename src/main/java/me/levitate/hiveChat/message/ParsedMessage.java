package me.levitate.hiveChat.message;

import me.levitate.hiveChat.placeholder.Placeholder;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ParsedMessage {
    private final List<MessageComponent> components = new ArrayList<>();

    public void addComponent(MessageComponent component) {
        components.add(component);
    }

    public void send(CommandSender sender, Placeholder... placeholders) {
        for (MessageComponent component : components) {
            component.send(sender, placeholders);
        }
    }

    public List<MessageComponent> getComponents() {
        return Collections.unmodifiableList(components);
    }
}