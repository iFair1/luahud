package me.ifair.luahud.config;

import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class ConfigData {
    static final int DEFAULT_LUA_TIMEOUT_MS = 25;
    static final int MAX_LUA_TIMEOUT_MS = 1000;

    boolean showHiddenFiles = false;
    boolean generateExampleProfiles = true;
    int luaTimeoutMs = DEFAULT_LUA_TIMEOUT_MS;
    List<ProfileData> profiles = new ArrayList<>();

    static boolean isValidProfileName(String name) {
        if (name == null || name.isBlank() || name.equals(".") || name.equals("..")
                || name.indexOf('/') >= 0 || name.indexOf('\\') >= 0) return false;
        try {
            Path path = Path.of(name + ".lua");
            return !path.isAbsolute() && path.getNameCount() == 1;
        } catch (InvalidPathException ignored) {
            return false;
        }
    }

    boolean normalize() {
        if (luaTimeoutMs < 0 || luaTimeoutMs > MAX_LUA_TIMEOUT_MS) {
            luaTimeoutMs = DEFAULT_LUA_TIMEOUT_MS;
            return true;
        }
        return false;
    }

    public static final class ProfileData {
        private String name;
        private boolean active = true;
        private String keybind = "key.keyboard.unknown";
        private boolean hidden = false;

        public ProfileData(String name) {
            this.name = name;
        }

        public String getName() {
            return this.name;
        }

        public boolean isActive() {
            return this.active;
        }

        public String getKeybind() {
            return this.keybind;
        }

        public boolean isHidden() {
            return this.hidden;
        }

        void setActive(boolean active) {
            this.active = active;
        }

        void setName(String name) {
            this.name = name;
        }

        void setKeybind(String keybind) {
            this.keybind = keybind;
        }

        void setHidden(boolean hidden) {
            this.hidden = hidden;
        }

        boolean normalize() {
            String normalized = normalizeKeybind(keybind);
            if (!normalized.equals(keybind)) {
                keybind = normalized;
                return true;
            }
            return false;
        }

        static String normalizeKeybind(String binding) {
            if (binding != null && !binding.isBlank()) {
                try {
                    InputUtil.Key key = InputUtil.fromTranslationKey(binding);
                    int code = key.getCode();
                    if (key.equals(InputUtil.UNKNOWN_KEY)
                            || (key.getCategory() == InputUtil.Type.MOUSE && code >= 0 && code <= GLFW.GLFW_MOUSE_BUTTON_LAST)
                            || (key.getCategory() == InputUtil.Type.KEYSYM && code >= GLFW.GLFW_KEY_SPACE && code <= GLFW.GLFW_KEY_LAST)
                            || (key.getCategory() == InputUtil.Type.SCANCODE && code >= 0)) {
                        return key.getTranslationKey();
                    }
                } catch (IllegalArgumentException ignored) {
                    // Invalid hand-edited bindings should behave as unbound keys.
                }
            }
            return "key.keyboard.unknown";
        }

        ProfileData copy() {
            ProfileData copy = new ProfileData(this.name);
            copy.active = this.active;
            copy.keybind = this.keybind;
            copy.hidden = this.hidden;
            return copy;
        }
    }

}
