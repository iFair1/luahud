package me.ifair.luahud.config;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ConfigData {
    public boolean showHiddenFiles = false;
    public boolean generateExampleProfiles = true;
    public Map<String, ProfileData> profiles = new ConcurrentHashMap<>();

    public static class ProfileData {
        public boolean active = true;
        public String keybind = "key.keyboard.unknown";
        public boolean hidden = false;
    }

}