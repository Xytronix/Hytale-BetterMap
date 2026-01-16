package dev.ninesliced.utils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.logging.Logger;

/**
 * Utility for performing reflection operations safely.
 */
public class ReflectionHelper {

    private static final Logger LOGGER = Logger.getLogger(ReflectionHelper.class.getName());

    /**
     * Gets a field from a class, setting it accessible.
     *
     * @param clazz     The class.
     * @param fieldName The field name.
     * @return The field, or null if not found.
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
     * Gets the value of a field from an instance.
     *
     * @param instance  The object instance.
     * @param fieldName The field name.
     * @return The value, or null on failure.
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
     * Sets the value of a field on an instance.
     *
     * @param instance  The object instance.
     * @param fieldName The field name.
     * @param value     The new value.
     * @return True if successful.
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
     * Gets a method from a class.
     *
     * @param clazz          The class.
     * @param methodName     The method name.
     * @param parameterTypes Parameter types.
     * @return The method, or null if not found.
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
     * Invokes a method on an instance.
     *
     * @param instance       The instance.
     * @param methodName     The method name.
     * @param parameterTypes Parameter types.
     * @param args           Arguments.
     * @return The return value, or null on failure.
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
     * Recursively searches for a field in the class hierarchy.
     *
     * @param clazz     The starting class.
     * @param fieldName The field name.
     * @return The field, or null if not found.
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
     * Gets value of a field found recursively in hierarchy.
     *
     * @param instance  The instance.
     * @param fieldName The field name.
     * @return The value.
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
     * Sets value of a field found recursively in hierarchy.
     *
     * @param instance  The instance.
     * @param fieldName The field name.
     * @param value     The new value.
     * @return True if successful.
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
