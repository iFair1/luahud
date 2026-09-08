package me.ifair.luahud.config;

import java.util.HashMap;
import java.util.Map;

public final class ConfigData {
    boolean showHiddenFiles = false;
    boolean generateExampleProfiles = true;
    Map<String, ProfileData> profiles = new HashMap<>();

    public static final class ProfileData {
        private boolean active = true;
        private String keybind = "key.keyboard.unknown";
        private boolean hidden = false;

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
            ProfileData copy = new ProfileData();
            copy.active = this.active;
            copy.keybind = this.keybind;
            copy.hidden = this.hidden;
            return copy;
        }
    }

}
