package me.ifair.luahud.core;

import me.ifair.luahud.config.ConfigManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Handles the configurable, per-profile bindings stored in luahud.json. */
public final class ProfileKeybindManager {
    private static final Map<InputUtil.Key, Boolean> pressedKeys = new HashMap<>();
    private static final Map<Integer, Integer> keysymsByScancode = new HashMap<>();
    private static Map<InputUtil.Key, List<String>> profilesByKey = Map.of();
    private static long loadedRevision = Long.MIN_VALUE;

    private ProfileKeybindManager() {
    }

    public static void tick(MinecraftClient client) {
        refreshBindingsIfNeeded();

        boolean canToggle = client.player != null && client.currentScreen == null;
        long window = client.getWindow().getHandle();
        for (Map.Entry<InputUtil.Key, List<String>> binding : profilesByKey.entrySet()) {
            InputUtil.Key key = binding.getKey();
            boolean pressed = isPressed(window, key);
            boolean wasPressed = pressedKeys.getOrDefault(key, false);
            pressedKeys.put(key, pressed);

            if (!canToggle || !pressed || wasPressed) continue;
            for (String profile : binding.getValue()) {
                ConfigManager.toggleProfileActive(profile);
            }
        }

        pressedKeys.keySet().retainAll(profilesByKey.keySet());
    }

    private static void refreshBindingsIfNeeded() {
        long revision = ConfigManager.getProfileKeybindRevision();
        if (loadedRevision == revision) return;

        Map<InputUtil.Key, List<String>> refreshed = new HashMap<>();
        ConfigManager.getProfiles().forEach(profile -> {
            InputUtil.Key key = parseKey(profile.getKeybind());
            if (key != null && !key.equals(InputUtil.UNKNOWN_KEY)) {
                refreshed.computeIfAbsent(key, ignored -> new ArrayList<>()).add(profile.getName());
            }
        });
        profilesByKey = refreshed;
        pressedKeys.keySet().retainAll(refreshed.keySet());
        loadedRevision = revision;
    }

    private static InputUtil.Key parseKey(String translationKey) {
        try {
            return InputUtil.fromTranslationKey(translationKey);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static boolean isPressed(long window, InputUtil.Key key) {
        if (key.getCategory() == InputUtil.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(window, key.getCode()) == GLFW.GLFW_PRESS;
        }
        if (key.getCategory() == InputUtil.Type.KEYSYM) {
            return InputUtil.isKeyPressed(window, key.getCode());
        }
        if (key.getCategory() == InputUtil.Type.SCANCODE) {
            int keysym = keysymsByScancode.computeIfAbsent(key.getCode(), ProfileKeybindManager::findKeysym);
            return keysym != GLFW.GLFW_KEY_UNKNOWN && InputUtil.isKeyPressed(window, keysym);
        }
        return false;
    }

    private static int findKeysym(int scancode) {
        for (int keysym = GLFW.GLFW_KEY_SPACE; keysym <= GLFW.GLFW_KEY_LAST; keysym++) {
            if (GLFW.glfwGetKeyScancode(keysym) == scancode) return keysym;
        }
        return GLFW.GLFW_KEY_UNKNOWN;
    }
}
