package dev.ifair.luahud.config;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigDataTest {
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "garbage", "key.keyboard.invalid", "key.mouse.9999", "scancode.-5", "key.keyboard.9999"})
    void invalidKeybindsNormalizeToUnbound(String binding) {
        ConfigData.ProfileData profile = new ConfigData.ProfileData("example");
        profile.setKeybind(binding);

        assertTrue(profile.normalize());

        assertEquals("key.keyboard.unknown", profile.getKeybind());
        assertFalse(profile.normalize(), "Normalization must be idempotent");
    }

    @ParameterizedTest
    @ValueSource(strings = {"key.keyboard.a", "key.keyboard.unknown", "key.mouse.left", "scancode.30"})
    void validKeybindsArePreserved(String binding) {
        ConfigData.ProfileData profile = new ConfigData.ProfileData("example");
        profile.setKeybind(binding);

        assertFalse(profile.normalize());
        assertEquals(binding, profile.getKeybind());
    }

    @Test
    void profilesSerializeAsAnOrderedListWithNames() {
        ConfigData config = new ConfigData();
        config.profiles.add(new ConfigData.ProfileData("second"));
        config.profiles.add(new ConfigData.ProfileData("first"));

        JsonObject json = JsonParser.parseString(new Gson().toJson(config)).getAsJsonObject();
        assertTrue(json.get("profiles").isJsonArray());
        assertEquals(ConfigData.DEFAULT_LUA_TIMEOUT_MS, json.get("luaTimeoutMs").getAsInt());

        JsonArray profiles = json.getAsJsonArray("profiles");
        assertEquals("second", profiles.get(0).getAsJsonObject().get("name").getAsString());
        assertEquals("first", profiles.get(1).getAsJsonObject().get("name").getAsString());
    }

    @Test
    void profileCopyPreservesSettingsAndCanBeEditedIndependently() {
        ConfigData.ProfileData profile = new ConfigData.ProfileData("example");
        profile.setActive(false);
        profile.setHidden(true);
        profile.setKeybind("key.keyboard.a");

        ConfigData.ProfileData copy = profile.copy();

        assertNotSame(profile, copy);
        assertEquals("example", copy.getName());
        assertFalse(copy.isActive());
        assertTrue(copy.isHidden());
        assertEquals("key.keyboard.a", copy.getKeybind());

        copy.setName("renamed");
        copy.setActive(true);
        copy.setHidden(false);
        copy.setKeybind("key.keyboard.b");

        assertEquals("example", profile.getName());
        assertFalse(profile.isActive());
        assertTrue(profile.isHidden());
        assertEquals("key.keyboard.a", profile.getKeybind());
    }

    @Test
    void luaTimeoutDefaultsAndNormalizesWhileAllowingDisabledMode() {
        ConfigData missing = new Gson().fromJson("{}", ConfigData.class);
        assertEquals(ConfigData.DEFAULT_LUA_TIMEOUT_MS, missing.luaTimeoutMs);

        ConfigData config = new ConfigData();
        config.luaTimeoutMs = 0;
        config.normalize();
        assertEquals(0, config.luaTimeoutMs);

        config.luaTimeoutMs = ConfigData.MAX_LUA_TIMEOUT_MS;
        config.normalize();
        assertEquals(ConfigData.MAX_LUA_TIMEOUT_MS, config.luaTimeoutMs);

        config.luaTimeoutMs = -1;
        config.normalize();
        assertEquals(ConfigData.DEFAULT_LUA_TIMEOUT_MS, config.luaTimeoutMs);

        config.luaTimeoutMs = ConfigData.MAX_LUA_TIMEOUT_MS + 1;
        config.normalize();
        assertEquals(ConfigData.DEFAULT_LUA_TIMEOUT_MS, config.luaTimeoutMs);
    }
    @Test void durationClockDefaultsAndNormalizesInvalidValues() {
        Gson gson = new Gson();
        assertEquals("realTime", gson.fromJson("{}", ConfigData.class).sectionDurationClock);
        ConfigData config = new ConfigData();
        config.sectionDurationClock = "gameTime";
        config.normalize();
        assertEquals("gameTime", config.sectionDurationClock);
        config.sectionDurationClock = "invalid";
        assertTrue(config.normalize());
        assertEquals("realTime", config.sectionDurationClock);
        config.sectionDurationClock = null;
        assertTrue(config.normalize());
        assertEquals("realTime", config.sectionDurationClock);
    }
}
