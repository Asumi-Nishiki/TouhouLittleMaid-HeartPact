package com.example.maidmarriage.client;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

/**
 * Optional Tweakeroo/Tweakerge freecam detection.
 *
 * <p>Do not link against Tweakeroo classes directly. Some packs ship forks or
 * different versions, and touching their fake LocalPlayer movement path is
 * dangerous. We only use this as a narrow guard to leave lap pillow before
 * freecam can drive its camera entity through vanilla player movement.
 */
public final class TweakerooFreecamCompat {
    private static final String CAMERA_ENTITY_CLASS = "fi.dy.masa.tweakeroo.util.CameraEntity";
    private static final String FEATURE_TOGGLE_CLASS = "fi.dy.masa.tweakeroo.config.FeatureToggle";
    private static final String[] FREECAM_FIELD_NAMES = {
            "TWEAK_FREE_CAMERA",
            "TWEAK_FREECAM",
            "FREE_CAMERA",
            "FREECAM"
    };
    private static final String[] BOOLEAN_METHOD_NAMES = {
            "getBooleanValue",
            "getValue",
            "isEnabled",
            "isOn"
    };

    private TweakerooFreecamCompat() {
    }

    public static boolean isFreecamActive(Minecraft minecraft) {
        if (minecraft == null || minecraft.player == null) {
            return false;
        }
        Entity cameraEntity = minecraft.getCameraEntity();
        if (cameraEntity != null && cameraEntity != minecraft.player && isTweakerooCameraEntity(cameraEntity)) {
            return true;
        }
        return isFeatureToggleActive();
    }

    private static boolean isTweakerooCameraEntity(Entity entity) {
        String className = entity.getClass().getName();
        return CAMERA_ENTITY_CLASS.equals(className)
                || (className.contains("tweakeroo") && className.endsWith("CameraEntity"));
    }

    private static boolean isFeatureToggleActive() {
        try {
            Class<?> featureToggleClass = Class.forName(FEATURE_TOGGLE_CLASS);
            for (String fieldName : FREECAM_FIELD_NAMES) {
                Field field = findField(featureToggleClass, fieldName);
                if (field == null) {
                    continue;
                }
                Object toggle = field.get(null);
                if (readBooleanToggle(toggle)) {
                    return true;
                }
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return false;
        }
        return false;
    }

    private static Field findField(Class<?> owner, String name) {
        try {
            Field field = owner.getField(name);
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException ignored) {
            return null;
        }
    }

    private static boolean readBooleanToggle(Object toggle) {
        if (toggle == null) {
            return false;
        }
        if (toggle instanceof Boolean value) {
            return value;
        }
        for (String methodName : BOOLEAN_METHOD_NAMES) {
            try {
                Method method = toggle.getClass().getMethod(methodName);
                method.setAccessible(true);
                Object value = method.invoke(toggle);
                if (value instanceof Boolean booleanValue && booleanValue) {
                    return true;
                }
            } catch (ReflectiveOperationException | LinkageError ignored) {
                // Try the next known accessor name.
            }
        }
        return false;
    }
}
