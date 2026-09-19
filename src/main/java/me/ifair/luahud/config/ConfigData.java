package me.ifair.luahud.config;

import java.util.ArrayList;
import java.util.List;

public final class ConfigData {
    boolean showHiddenFiles = false;
    boolean generateExampleProfiles = true;
    List<ProfileData> profiles = new ArrayList<>();

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
