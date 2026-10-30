package dev.ifair.luahud.api.models;

import dev.ifair.luahud.api.LuaApiRegistrar;
import dev.ifair.luahud.api.ModelBuilder;
import dev.ifair.luahud.core.Profile;

public final class ProfileModel {
    private ProfileModel() {}

    public static ModelBuilder<Profile> register(LuaApiRegistrar api) {
        ModelBuilder<Profile> model = api.model("Profile", Profile.class);
        model.string("name", "Profile name", Profile::name);
        model.bool("active", "Whether the profile is active", Profile::active);
        model.bool("hidden", "Whether the profile is hidden", Profile::hidden);
        model.string("keybind", "Profile key binding", Profile::keybind);
        return model;
    }
}
