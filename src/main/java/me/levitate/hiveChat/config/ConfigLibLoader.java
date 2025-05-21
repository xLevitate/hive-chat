package me.levitate.hiveChat.config;

import me.levitate.hiveChat.message.MessageRegistry;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Utility to load messages from ConfigLib configurations
 */
public class ConfigLibLoader {
    
    /**
     * Load messages from a ConfigLib configuration class
     * This supports String fields and Map<String, String> fields
     * 
     * @param configInstance Instance of the ConfigLib configuration
     * @param registry MessageRegistry to load into
     * @param baseKey Base key to prefix all message keys
     * @param logger Logger for errors
     * @return Number of messages loaded
     */
    public static int loadFromConfig(Object configInstance, MessageRegistry registry, String baseKey, Logger logger) {
        if (configInstance == null || registry == null) {
            return 0;
        }
        
        int count = 0;
        Class<?> configClass = configInstance.getClass();
        baseKey = baseKey == null ? "" : baseKey.endsWith(".") ? baseKey : baseKey + ".";
        
        // Look for getters to handle both public fields and methods
        for (Method method : configClass.getMethods()) {
            String methodName = method.getName();
            if (methodName.startsWith("get") && !methodName.equals("getClass") && method.getParameterCount() == 0) {
                try {
                    String fieldName = methodName.substring(3, 4).toLowerCase() + methodName.substring(4);
                    Object value = method.invoke(configInstance);
                    
                    count += processValue(fieldName, value, registry, baseKey, logger);
                } catch (Exception e) {
                    if (logger != null) {
                        logger.warning("Error loading message from " + methodName + ": " + e.getMessage());
                    }
                }
            }
        }
        
        // Also check direct field access for public fields
        for (Field field : configClass.getFields()) {
            try {
                String fieldName = field.getName();
                Object value = field.get(configInstance);
                
                // Skip fields we've already processed via getters
                if (hasGetter(configClass, fieldName)) {
                    continue;
                }
                
                count += processValue(fieldName, value, registry, baseKey, logger);
            } catch (Exception e) {
                if (logger != null) {
                    logger.warning("Error loading message from field " + field.getName() + ": " + e.getMessage());
                }
            }
        }
        
        return count;
    }
    
    /**
     * Check if a class has a getter for the given field name
     * @param clazz Class to check
     * @param fieldName Field name to check for
     * @return true if a getter exists, false otherwise
     */
    private static boolean hasGetter(Class<?> clazz, String fieldName) {
        try {
            String getterName = "get" + fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1);
            clazz.getMethod(getterName);
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }
    
    /**
     * Process a value from a field or method
     * @param name Field or method name
     * @param value Value to process
     * @param registry MessageRegistry to load into
     * @param baseKey Base key prefix
     * @param logger Logger for errors
     * @return Number of messages added
     */
    @SuppressWarnings("unchecked")
    private static int processValue(String name, Object value, MessageRegistry registry, String baseKey, Logger logger) {
        if (value == null) {
            return 0;
        }
        
        int count = 0;
        
        // Handle different value types
        if (value instanceof String string) {
            registry.register(baseKey + name, string);
            count++;
        } else if (value instanceof Map<?, ?> map) {
            try {
                Map<String, String> stringMap = (Map<String, String>) map;
                for (Map.Entry<String, String> entry : stringMap.entrySet()) {
                    if (entry.getValue() != null) {
                        registry.register(baseKey + name + "." + entry.getKey(), entry.getValue());
                        count++;
                    }
                }
            } catch (ClassCastException e) {
                if (logger != null) {
                    logger.warning("Map field " + name + " does not contain String values");
                }
            }
        } else if (value instanceof Collection<?> collection) {
            int index = 0;
            for (Object item : collection) {
                if (item instanceof String string) {
                    registry.register(baseKey + name + "." + index, string);
                    count++;
                }
                index++;
            }
        } else if (isConfigObject(value)) {
            // Recursively process nested ConfigLib objects
            count += loadFromConfig(value, registry, baseKey + name, logger);
        }
        
        return count;
    }
    
    /**
     * Check if an object is a ConfigLib configuration object
     * @param object Object to check
     * @return true if the object is a ConfigLib configuration
     */
    private static boolean isConfigObject(Object object) {
        if (object == null) {
            return false;
        }
        
        Class<?> clazz = object.getClass();
        
        // Check for common configuration object patterns
        String className = clazz.getName();
        if (className.contains("Config") || className.contains("Configuration") || 
            className.contains("Settings") || className.contains("Messages")) {
            return true;
        }
        
        // Check for known annotations by name to avoid class loading issues
        for (Annotation annotation : clazz.getAnnotations()) {
            String annotationName = annotation.annotationType().getName();
            if (annotationName.endsWith("Configuration") || 
                annotationName.endsWith("ConfigurationElement") ||
                annotationName.endsWith("ConfSerialization")) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Create a message map from fields in an object
     * @param object The object containing message fields 
     * @param prefix Prefix for the message keys
     * @return A map of message keys to message strings
     */
    public static Map<String, String> createMessageMap(Object object, String prefix) {
        Map<String, String> messages = new HashMap<>();
        if (object == null) {
            return messages;
        }
        
        prefix = prefix == null ? "" : prefix.endsWith(".") ? prefix : prefix + ".";
        Class<?> clazz = object.getClass();
        
        // Try public fields first
        for (Field field : clazz.getFields()) {
            try {
                Object value = field.get(object);
                if (value instanceof String) {
                    messages.put(prefix + field.getName(), (String) value);
                }
            } catch (Exception ignored) {
                // Skip inaccessible fields
            }
        }
        
        // Try getters next
        for (Method method : clazz.getMethods()) {
            if (method.getName().startsWith("get") && !method.getName().equals("getClass") && 
                    method.getParameterCount() == 0 && 
                    method.getReturnType() == String.class) {
                try {
                    String fieldName = method.getName().substring(3, 4).toLowerCase() + method.getName().substring(4);
                    Object value = method.invoke(object);
                    if (value instanceof String) {
                        messages.put(prefix + fieldName, (String) value);
                    }
                } catch (Exception ignored) {
                    // Skip methods that can't be called
                }
            }
        }
        
        return messages;
    }
} 