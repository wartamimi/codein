package org.jetbrains.kotlin.com.intellij.util.containers;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Android replacement for the IntelliJ {@code Unsafe} wrapper inside kotlin-compiler-embeddable.
 *
 * <p>The original binds every {@code sun.misc.Unsafe} method with MethodHandles in its static
 * initializer and fails as a whole because Android's Unsafe has no {@code copyMemory(Object, long,
 * Object, long, long)}. Codein loads the dexed compiler with its own class loader as the parent, so
 * this class (same name, same public API) is the one the compiler links against. All operations
 * delegate to Android's {@code sun.misc.Unsafe} through reflection; {@link #copyMemory} is
 * implemented for primitive arrays, which is how the compiler uses it.
 */
public final class Unsafe {

    private static final Object UNSAFE;
    private static final Method PUT_OBJECT_VOLATILE;
    private static final Method GET_OBJECT_VOLATILE;
    private static final Method CAS_OBJECT;
    private static final Method CAS_INT;
    private static final Method CAS_LONG;
    private static final Method GET_AND_ADD_INT;
    private static final Method OBJECT_FIELD_OFFSET;
    private static final Method ARRAY_INDEX_SCALE;
    private static final Method ARRAY_BASE_OFFSET;

    static {
        try {
            Class<?> type = Class.forName("sun.misc.Unsafe");
            Field field;
            try {
                field = type.getDeclaredField("theUnsafe");
            } catch (NoSuchFieldException e) {
                field = type.getDeclaredField("THE_ONE");
            }
            field.setAccessible(true);
            UNSAFE = field.get(null);
            PUT_OBJECT_VOLATILE = type.getMethod("putObjectVolatile", Object.class, long.class, Object.class);
            GET_OBJECT_VOLATILE = type.getMethod("getObjectVolatile", Object.class, long.class);
            CAS_OBJECT = type.getMethod("compareAndSwapObject", Object.class, long.class, Object.class, Object.class);
            CAS_INT = type.getMethod("compareAndSwapInt", Object.class, long.class, int.class, int.class);
            CAS_LONG = type.getMethod("compareAndSwapLong", Object.class, long.class, long.class, long.class);
            GET_AND_ADD_INT = findOptional(type, "getAndAddInt", Object.class, long.class, int.class);
            OBJECT_FIELD_OFFSET = type.getMethod("objectFieldOffset", Field.class);
            ARRAY_INDEX_SCALE = type.getMethod("arrayIndexScale", Class.class);
            ARRAY_BASE_OFFSET = type.getMethod("arrayBaseOffset", Class.class);
        } catch (Exception e) {
            throw new Error(e);
        }
    }

    private Unsafe() {
    }

    private static Method findOptional(Class<?> type, String name, Class<?>... parameters) {
        try {
            return type.getMethod(name, parameters);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private static Object call(Method method, Object... args) {
        try {
            return method.invoke(UNSAFE, args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            if (cause instanceof Error) throw (Error) cause;
            throw new RuntimeException(cause);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public static boolean compareAndSwapInt(Object object, long offset, int expected, int value) {
        return (Boolean) call(CAS_INT, object, offset, expected, value);
    }

    public static boolean compareAndSwapLong(Object object, long offset, long expected, long value) {
        return (Boolean) call(CAS_LONG, object, offset, expected, value);
    }

    public static int getAndAddInt(Object object, long offset, int delta) {
        if (GET_AND_ADD_INT != null) return (Integer) call(GET_AND_ADD_INT, object, offset, delta);
        while (true) {
            int current = (Integer) call(findIntVolatile(), object, offset);
            if (compareAndSwapInt(object, offset, current, current + delta)) return current;
        }
    }

    private static volatile Method intVolatile;

    private static Method findIntVolatile() {
        Method method = intVolatile;
        if (method == null) {
            try {
                method = UNSAFE.getClass().getMethod("getIntVolatile", Object.class, long.class);
            } catch (NoSuchMethodException e) {
                throw new Error(e);
            }
            intVolatile = method;
        }
        return method;
    }

    public static Object getObjectVolatile(Object object, long offset) {
        return call(GET_OBJECT_VOLATILE, object, offset);
    }

    public static boolean compareAndSwapObject(Object object, long offset, Object expected, Object value) {
        return (Boolean) call(CAS_OBJECT, object, offset, expected, value);
    }

    public static void putObjectVolatile(Object object, long offset, Object value) {
        call(PUT_OBJECT_VOLATILE, object, offset, value);
    }

    public static long objectFieldOffset(Field field) {
        return (Long) call(OBJECT_FIELD_OFFSET, field);
    }

    public static int arrayIndexScale(Class<?> arrayClass) {
        return (Integer) call(ARRAY_INDEX_SCALE, arrayClass);
    }

    public static int arrayBaseOffset(Class<?> arrayClass) {
        return (Integer) call(ARRAY_BASE_OFFSET, arrayClass);
    }

    /** Copies between two primitive arrays of the same type, addressed by Unsafe offsets. */
    public static void copyMemory(Object source, long sourceOffset, Object target, long targetOffset, long bytes) {
        if (source == null || target == null || source.getClass() != target.getClass() || !source.getClass().isArray()
                || !source.getClass().getComponentType().isPrimitive()) {
            throw new UnsupportedOperationException("copyMemory is only supported between primitive arrays of the same type on Android");
        }
        Class<?> arrayClass = source.getClass();
        int base = arrayBaseOffset(arrayClass);
        int scale = arrayIndexScale(arrayClass);
        int sourceIndex = (int) ((sourceOffset - base) / scale);
        int targetIndex = (int) ((targetOffset - base) / scale);
        int length = (int) (bytes / scale);
        if (sourceIndex < 0 || targetIndex < 0 || sourceIndex + length > Array.getLength(source) || targetIndex + length > Array.getLength(target)) {
            throw new IndexOutOfBoundsException("copyMemory out of bounds");
        }
        System.arraycopy(source, sourceIndex, target, targetIndex, length);
    }
}
