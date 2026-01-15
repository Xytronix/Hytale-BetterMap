package dev.ninesliced.exploration;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Utility class for reflection operations needed to modify map behavior.
 * Provides safe reflection access with error handling and logging.
 */
public class ReflectionHelper {
    
    private static final Logger LOGGER = Logger.getLogger(ReflectionHelper.class.getName());

    /**
     * Get a field from a class, making it accessible if needed
     */
    @Nullable
    public static Field getField(@Nonnull Class<?> clazz, @Nonnull String fieldName) {
        try {
            Field field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException e) {
            LOGGER.warning("Field not found: " + clazz.getName() + "." + fieldName);
            return null;
        }
    }

    /**
     * Get the value of a field from an object instance
     */
    @Nullable
    public static Object getFieldValue(@Nonnull Object instance, @Nonnull String fieldName) {
        try {
            Field field = getField(instance.getClass(), fieldName);
            if (field != null) {
                return field.get(instance);
            }
        } catch (IllegalAccessException e) {
            LOGGER.warning("Cannot access field: " + fieldName);
        }
        return null;
    }

    /**
     * Set the value of a field on an object instance
     */
    public static boolean setFieldValue(@Nonnull Object instance, @Nonnull String fieldName, @Nullable Object value) {
        try {
            Field field = getField(instance.getClass(), fieldName);
            if (field != null) {
                field.set(instance, value);
                return true;
            }
        } catch (IllegalAccessException e) {
            LOGGER.warning("Cannot access field for setting: " + fieldName);
        }
        return false;
    }

    /**
     * Get a method from a class by name and parameter types
     */
    @Nullable
    public static Method getMethod(@Nonnull Class<?> clazz, @Nonnull String methodName, Class<?>... parameterTypes) {
        try {
            Method method = clazz.getDeclaredMethod(methodName, parameterTypes);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException e) {
            LOGGER.warning("Method not found: " + clazz.getName() + "." + methodName);
            return null;
        }
    }

    /**
     * Invoke a method on an object instance
     */
    @Nullable
    public static Object invokeMethod(@Nonnull Object instance, @Nonnull String methodName, Class<?>[] parameterTypes, Object[] args) {
        try {
            Method method = getMethod(instance.getClass(), methodName, parameterTypes);
            if (method != null) {
                return method.invoke(instance, args);
            }
        } catch (Exception e) {
            LOGGER.warning("Cannot invoke method: " + methodName + " - " + e.getMessage());
        }
        return null;
    }

    /**
     * Get a field from an object, trying parent classes if not found
     */
    @Nullable
    public static Field getFieldRecursive(@Nonnull Class<?> clazz, @Nonnull String fieldName) {
        Class<?> current = clazz;
        while (current != null) {
            try {
                Field field = current.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    /**
     * Get a field value from an object, searching parent classes if needed
     */
    @Nullable
    public static Object getFieldValueRecursive(@Nonnull Object instance, @Nonnull String fieldName) {
        try {
            Field field = getFieldRecursive(instance.getClass(), fieldName);
            if (field != null) {
                return field.get(instance);
            }
        } catch (IllegalAccessException e) {
            LOGGER.warning("Cannot access field recursively: " + fieldName);
        }
        return null;
    }

    /**
     * Set a field value on an object, searching parent classes if needed
     */
    public static boolean setFieldValueRecursive(@Nonnull Object instance, @Nonnull String fieldName, @Nullable Object value) {
        try {
            Field field = getFieldRecursive(instance.getClass(), fieldName);
            if (field != null) {
                field.set(instance, value);
                return true;
            }
        } catch (IllegalAccessException e) {
            LOGGER.warning("Cannot access field recursively for setting: " + fieldName);
        }
        return false;
    }
}
