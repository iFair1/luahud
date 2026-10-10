package me.ifair.luahud.render;

/** Carries per-icon lighting through the deferred GUI renderer in 1.21.6–1.21.8. */
public interface GuiEntityLight {
    int luahud$getGuiLight();
    void luahud$setGuiLight(int light);
}
