package me.levitate.hiveChat.placeholder;

import java.util.Objects;

public class Placeholder {
    private final String key;
    private final String value;

    private Placeholder(String key, String value) {
        this.key = key;
        this.value = value != null ? value : "";
    }

    public static Placeholder of(String key, String value) {
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("Placeholder key cannot be null or empty");
        }
        return new Placeholder(key, value);
    }

    public static Placeholder of(String key, int value) {
        return of(key, String.valueOf(value));
    }

    public static Placeholder of(String key, double value) {
        return of(key, String.valueOf(value));
    }

    public static Placeholder of(String key, boolean value) {
        return of(key, String.valueOf(value));
    }

    public static Placeholder of(String key, Object value) {
        return of(key, value != null ? value.toString() : "");
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Placeholder that = (Placeholder) o;
        return Objects.equals(key, that.key) &&
                Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, value);
    }

    @Override
    public String toString() {
        return String.format("{%s}", key);
    }
}