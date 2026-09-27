package me.ifair.luahud.gui.components;

import me.ifair.luahud.config.ConfigManager;
import me.ifair.luahud.gui.LuaHUDScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.ElementListWidget;

public class ProfileListWidget extends ElementListWidget<ProfileEntry> {
    private static final int MAX_ROW_WIDTH = 400;
    private static final int TOP_MARGIN = HeaderWidget.MARGIN * 2 + HeaderWidget.BUTTON_SIZE;
    private static final int BOTTOM_MARGIN = FooterWidget.MARGIN * 2 + HeaderWidget.BUTTON_SIZE;

    private final LuaHUDScreen screen;
    private long profileRevision = -1;

   public ProfileListWidget(LuaHUDScreen screen) {
       super(
               MinecraftClient.getInstance(),
               screen.width,
               screen.height - TOP_MARGIN - BOTTOM_MARGIN,
               TOP_MARGIN,
               ProfileEntry.ROW_HEIGHT
       );
       this.screen = screen;
   }

   public void build() {
       reloadProfiles();
       screen.addWidget(this);
   }

    public void reloadProfiles() {
        long reloadedRevision = ConfigManager.getProfileRevision();
        ProfileEntry focused = getFocused();
        if (focused != null) focused.setFocused(null);
        setFocused(null);
        clearEntries();

        boolean showHidden = ConfigManager.isShowHiddenFilesEnabled();

        ConfigManager.getProfiles().stream()
                .filter(profile -> showHidden || !profile.isHidden())
                .forEach(profile -> {
                    addEntry(new ProfileEntry(this, profile));
                });
        profileRevision = reloadedRevision;
    }

    public void refreshIfChanged() {
        if (profileRevision != ConfigManager.getProfileRevision()) reloadProfiles();
    }

    public void selectKeybind(ProfileEntry entry) {
        screen.selectKeybind(entry);
    }

   public void moveProfile(String name, int offset) {
       ConfigManager.moveProfile(name, offset);
       reloadProfiles();
   }

   public boolean cancelNameEdit() {
       ProfileEntry focused = getFocused();
       return (focused != null) && focused.cancelNameEditIfFocused();
   }

   @Override
    public int getRowWidth() { return Math.min(MAX_ROW_WIDTH, width - 32); }

    @Override
    protected int getScrollbarX() { return getRight() - 8; }
}
