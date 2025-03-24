package me.levitate.hiveChat.message;

import me.levitate.hiveChat.placeholder.Placeholder;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ParsedMessage {
    private final List<MessageComponent> components = new ArrayList<>();

    /**
     * Add a component to this message
     * @param component The component to add
     */
    public void addComponent(MessageComponent component) {
        components.add(component);
    }

    /**
     * Send this message to a command sender
     * @param sender The command sender to send to
     * @param placeholders Placeholders to apply
     */
    public void send(CommandSender sender, Placeholder... placeholders) {
        for (MessageComponent component : components) {
            component.send(sender, placeholders);
        }
    }

    /**
     * Get all components in this message
     * @return Unmodifiable list of components
     */
    public List<MessageComponent> getComponents() {
        return Collections.unmodifiableList(components);
    }

    /**
     * Check if this message has any components
     * @return true if this message has components, false otherwise
     */
    public boolean hasComponents() {
        return !components.isEmpty();
    }

    /**
     * Create a deep copy of this message
     * Useful for reusing cached messages
     * @return A new instance with the same components
     */
    public ParsedMessage copy() {
        ParsedMessage copy = new ParsedMessage();

        // Deep copy the components
        for (MessageComponent component : this.components) {
            // Create a new component with the same properties
            MessageComponent newComponent = new MessageComponent();

            // Copy only the non-null properties
            if (component.getContent() != null) {
                newComponent.setContent(component.getContent());
            }

            if (component.getSound() != null) {
                newComponent.setSound(component.getSound())
                        .setVolume(component.getVolume())
                        .setPitch(component.getPitch());
            }

            if (component.getActionBar() != null) {
                newComponent.setActionBar(component.getActionBar());
            }

            if (component.getBossBar() != null) {
                newComponent.setBossBar(component.getBossBar());
            }

            if (component.getTitle() != null) {
                newComponent.setTitle(component.getTitle());
            }

            copy.addComponent(newComponent);
        }

        return copy;
    }
}