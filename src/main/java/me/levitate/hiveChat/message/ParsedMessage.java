package me.levitate.hiveChat.message;

import me.levitate.hiveChat.placeholder.Placeholder;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ParsedMessage {
    private final List<MessageComponent> components = new ArrayList<>();
    
    public void addComponent(MessageComponent component) {
        components.add(component);
    }
    
    public void send(Player player, Placeholder... placeholders) {
        for (MessageComponent component : components) {
            component.send(player, placeholders);
        }
    }
    
    public List<MessageComponent> getComponents() {
        return Collections.unmodifiableList(components);
    }
}