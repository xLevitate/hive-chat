# HiveChat

A powerful Minecraft message library for sending formatted messages with advanced features.

## Installation

### Gradle

```kotlin
repositories {
    maven("https://jitpack.io")
}

dependencies {
    implementation("com.github.xLevitate:hive-chat:1.3.1")
}
```

### Maven

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>com.github.xLevitate</groupId>
        <artifactId>hive-chat</artifactId>
        <version>1.3.1</version>
    </dependency>
</dependencies>
```

## Usage

### Initialization

Initialize HiveChat in your plugin's `onEnable` method:

```java
@Override
public void onEnable() {
    HiveChat.init(this);
}
```

### Basic Messages

Send messages to players or command senders:

```java
// Send a simple message
HiveChat.send(player, "Hello, world!");

// Add color using MiniMessage format
HiveChat.send(player, "<red>This message is red</red>");

// Send to a command sender (player or console)
HiveChat.send(sender, "<green>Command executed successfully!</green>");
```

### Placeholders

Use placeholders to customize messages:

```java
// Create placeholders
Placeholder namePlaceholder = Placeholder.of("name", player.getName());
Placeholder scorePlaceholder = Placeholder.of("score", 100);

// Use placeholders in messages
HiveChat.send(player, "Hello, {name}! Your score is {score}.", namePlaceholder, scorePlaceholder);
```

### Universal Placeholders

Define placeholders that are automatically applied to all messages:

```java
// Add a static universal placeholder
HiveChat.addUniversalPlaceholder("prefix", "<gold>[MyServer]</gold> ");

// Add a dynamic placeholder based on player context
HiveChat.addDynamicPlaceholder("health", player -> String.valueOf(player.getHealth()));

// Now all messages can use {prefix} and {health} without explicitly defining them
HiveChat.send(player, "{prefix} Your health: {health}");
```

### Advanced Components

Send titles, action bars, boss bars, and sounds:

```java
// Title with subtitle
HiveChat.send(player, "<title>Main Title|This is the subtitle</title>");

// Title with custom timing (fadeIn:stay:fadeOut in ticks)
HiveChat.send(player, "<title:10:70:20>Custom Timing|Fade in, stay, fade out</title>");

// Action bar
HiveChat.send(player, "<actionbar>This appears above the hotbar</actionbar>");

// Action bar with duration (in ticks)
HiveChat.send(player, "<actionbar:60>This stays for 3 seconds</actionbar>");

// Boss bar (format: color:style:progress:duration)
HiveChat.send(player, "<bossbar:RED:SOLID:1.0:100>Boss Message</bossbar>");

// Sound effects (format: sound:volume:pitch)
HiveChat.send(player, "<sound:ENTITY_PLAYER_LEVELUP:1.0:1.0> You leveled up!");
```

### Message Chains

Create chains of messages that play in sequence:

```java
HiveChat.createChain()
    .then("First message")
    .thenWait(20) // Wait 1 second (20 ticks)
    .then("<red>Second message</red>")
    .thenWait(20)
    .then("<actionbar>Action bar message</actionbar>")
    .thenWait(20)
    .then("<title>Title|Subtitle</title>")
    .send(player);
```

### Message Registry

Store messages for easy access:

```java
// Register a message
HiveChat.registerMessage("welcome", "<gold>Welcome to the server, {player}!");

// Send the registered message
HiveChat.sendRegistered("welcome", player, Placeholder.of("player", player.getName()));
```

### ConfigLib Integration

Load messages from your ConfigLib configuration:

```java
// Assuming you have a ConfigLib Messages class
Messages messages = new Messages();
messages.load();

// Import messages into the registry
HiveChat.getMessageRegistry().importMessages(ConfigLibLoader.createMessageMap(messages, "messages"));

// Alternative: Load an entire configuration section
ConfigLibLoader.loadFromConfig(messages, HiveChat.getMessageRegistry(), "messages", getLogger());

// Now you can use them by key
HiveChat.sendRegistered("messages.welcome", player);
```

### Message Lists

Send multiple messages in sequence:

```java
List<String> messages = Arrays.asList(
    "<green>First message</green>",
    "<yellow>Second message</yellow>",
    "<red>Third message</red>"
);

HiveChat.sendList(player, messages);
```

### Broadcasting

Broadcast messages to all players:

```java
// Broadcast to all online players
HiveChat.broadcast("<red>Server restarting in 5 minutes!</red>");

// Broadcast to players within a radius
HiveChat.broadcast(50, centralLocation, "<yellow>Event starting nearby!</yellow>");
```
