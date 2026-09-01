package me.ifair.luahud.config;

import java.util.HashMap;
import java.util.Map;

public class ConfigData {
    public boolean showHiddenFiles = false;
    public boolean generateExampleProfiles = true;
    public Map<String, ProfileData> profiles = new HashMap<>();

    public static class ProfileData {
        public boolean active = false;
        public String keybind = "key.keyboard.unknown";
    }

}