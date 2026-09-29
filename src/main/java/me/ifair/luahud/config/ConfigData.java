package me.ifair.luahud.config;

import java.util.ArrayList;
import java.util.List;

public final class ConfigData {
    static final int DEFAULT_LUA_TIMEOUT_MS = 25;
    static final int MAX_LUA_TIMEOUT_MS = 1000;

    boolean showHiddenFiles = false;
    boolean generateExampleProfiles = true;
    int luaTimeoutMs = DEFAULT_LUA_TIMEOUT_MS;
    List<ProfileData> profiles = new ArrayList<>();

    void normalize() {
        if (luaTimeoutMs < 0 || luaTimeoutMs > MAX_LUA_TIMEOUT_MS) {
            luaTimeoutMs = DEFAULT_LUA_TIMEOUT_MS;
        }
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

        void normalize() {
            if (this.keybind == null || this.keybind.isBlank()) {
                this.keybind = "key.keyboard.unknown";
            }
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
