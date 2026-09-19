package me.ifair.luahud.config;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigDataTest {
    @Test
    void profilesSerializeAsAnOrderedListWithNames() {
        ConfigData config = new ConfigData();
        config.profiles.add(new ConfigData.ProfileData("second"));
        config.profiles.add(new ConfigData.ProfileData("first"));

        JsonObject json = JsonParser.parseString(new Gson().toJson(config)).getAsJsonObject();
        assertTrue(json.get("profiles").isJsonArray());

        JsonArray profiles = json.getAsJsonArray("profiles");
        assertEquals("second", profiles.get(0).getAsJsonObject().get("name").getAsString());
        assertEquals("first", profiles.get(1).getAsJsonObject().get("name").getAsString());
    }

    @Test
    void profileCopyRetainsItsName() {
        ConfigData.ProfileData profile = new ConfigData.ProfileData("example");

        assertEquals("example", profile.copy().getName());
    }
}
