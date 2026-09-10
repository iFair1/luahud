package me.ifair.luahud.gui;

import me.ifair.luahud.LuaHUD;
import me.ifair.luahud.config.ConfigData;
import me.ifair.luahud.config.ConfigManager;
import me.ifair.luahud.error.ErrorManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class LuaHUDScreen extends Screen {
    private static final String GITHUB_URL = "https://github.com/iFair1/luahud";
    private static final String MODRINTH_URL = "https://modrinth.com/mod/luahud";
    private static final Path PROFILES_DIR = ConfigManager.getScriptsDir();

    static final int CONTENT_MAX_WIDTH = 540;
    static final int BUTTON_HEIGHT = 20;
    static final int ROW_HEIGHT = 20;
    static final int ROW_CONTROL_GAP = 4;
    static final int ACTIVE_BUTTON_WIDTH = 36;
    static final int KEYBIND_BUTTON_WIDTH = 72;
    static final int HIDE_BUTTON_WIDTH = 44;
    static final int EDIT_BUTTON_WIDTH = 44;

    private final Screen parent;
    private List<ProfileEntry> profiles = List.of();
    @Nullable
    private ProfileEntryWidget focusedProfile;
    @Nullable
    private ProfileEntryWidget keybindProfile;
    private int contentLeft;
    private int contentWidth;
    private int listTop;
    private int listBottom;
    private ButtonWidget errorsButton;

    public LuaHUDScreen(@Nullable Screen parent) {
        super(Text.literal("LuaHUD"));
        this.parent = parent;
    }

    public LuaHUDScreen() {
        this(null);
    }

    @Override
    protected void init() {
        this.contentWidth = Math.min(CONTENT_MAX_WIDTH, this.width - 24);
        this.contentLeft = (this.width - this.contentWidth) / 2;
        this.listTop = 52;
        this.listBottom = Math.max(this.listTop + ROW_HEIGHT, this.height - 40);

        reloadProfiles();
        this.addDrawableChild(new ProfileListWidget(
                this.client,
                this.width,
                this.listBottom - this.listTop,
                this.listTop,
                ROW_HEIGHT,
                this.profiles,
                this.textRenderer,
                this
        ));

        int topY = 20;
        this.addDrawableChild(ButtonWidget.builder(Text.literal("GitHub"), button -> openInBrowser(GITHUB_URL))
                .dimensions(this.contentLeft, topY, 60, BUTTON_HEIGHT)
                .build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Modrinth"), button -> openInBrowser(MODRINTH_URL))
                .dimensions(this.contentLeft + 64, topY, 70, BUTTON_HEIGHT)
                .build());

        String hiddenLabel = ConfigManager.isShowHiddenFilesEnabled() ? "Hidden: On" : "Hidden: Off";
        this.addDrawableChild(ButtonWidget.builder(Text.literal(hiddenLabel), button -> {
                    ConfigManager.setShowHiddenFiles(!ConfigManager.isShowHiddenFilesEnabled());
                    this.clearAndInit();
                })
                .dimensions(this.contentLeft + this.contentWidth - 100, topY, 100, BUTTON_HEIGHT)
                .build());

        int footerY = this.height - 28;
        int footerButtonWidth = Math.min(150, (this.contentWidth - 20) / 3);
        int footerWidth = footerButtonWidth * 3 + 20;
        int footerX = (this.width - footerWidth) / 2;
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Open Folder"), button -> openProfilesFolder())
                .dimensions(footerX, footerY, footerButtonWidth, BUTTON_HEIGHT)
                .build());
        this.errorsButton = this.addDrawableChild(ButtonWidget.builder(
                        Text.literal("Errors (" + ErrorManager.distinctCount() + ")"),
                        button -> this.client.setScreen(new LuaHUDErrorsScreen(this, null)))
                .dimensions(footerX + footerButtonWidth + 10, footerY, footerButtonWidth, BUTTON_HEIGHT)
                .build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Done"), button -> this.close())
                .dimensions(footerX + (footerButtonWidth + 10) * 2, footerY, footerButtonWidth, BUTTON_HEIGHT)
                .build());
    }

    @Override
    public void tick() {
        super.tick();
        if (this.errorsButton != null) {
            this.errorsButton.setMessage(Text.literal("Errors (" + ErrorManager.distinctCount() + ")"));
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 26, 0xFFFFFF);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.keybindProfile != null) {
            setSelectedKeybind(InputUtil.Type.MOUSE.createFromCode(button));
            return true;
        }

        boolean handled = super.mouseClicked(mouseX, mouseY, button);
        if (this.focusedProfile != null && !this.focusedProfile.isNameFieldMouseOver(mouseX, mouseY)) {
            this.focusedProfile.cancelEditing();
            return true;
        }
        return handled;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.keybindProfile != null) {
            setSelectedKeybind(keyCode == GLFW.GLFW_KEY_ESCAPE
                    ? InputUtil.UNKNOWN_KEY
                    : InputUtil.fromKeyCode(keyCode, scanCode));
            return true;
        }

        if (this.focusedProfile != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                this.focusedProfile.cancelEditing();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                this.focusedProfile.commitRename();
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(this.parent);
        }
    }

    private void reloadProfiles() {
        boolean showHidden = ConfigManager.isShowHiddenFilesEnabled();
        this.profiles = ConfigManager.getProfiles().entrySet().stream()
                .filter(entry -> showHidden || !entry.getValue().isHidden())
                .sorted(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER))
                .map(entry -> new ProfileEntry(entry.getKey(), entry.getValue()))
                .toList();
    }

    private static void openProfilesFolder() {
        try {
            Files.createDirectories(PROFILES_DIR);
            Util.getOperatingSystem().open(PROFILES_DIR);
        } catch (IOException e) {
            LuaHUD.LOGGER.error("Error while opening profiles directory at {}: {}", PROFILES_DIR, e.getMessage());
            ErrorManager.reportSystem("open profiles folder", e);
        }
    }

    static void editProfile(String profileName) {
        Path profilePath = PROFILES_DIR.resolve(profileName + ".lua").normalize();
        if (!PROFILES_DIR.equals(profilePath.getParent())) {
            LuaHUD.LOGGER.error("Refusing to open profile outside the profiles directory: {}", profileName);
            ErrorManager.reportSystem("open profile", "Refusing to open profile outside the profiles directory: " + profileName);
            return;
        }

        Util.getOperatingSystem().open(profilePath);
    }

    private static void openInBrowser(String url) {
        try {
            Util.getOperatingSystem().open(new URI(url));
        } catch (Exception e) {
            LuaHUD.LOGGER.error("There was an error while directing to url \"{}\": {}", url, e.getMessage());
            ErrorManager.reportSystem("open URL", e);
        }
    }

    @Nullable
    ProfileEntryWidget getFocusedProfile() {
        return this.focusedProfile;
    }

    void setFocusedProfile(@Nullable ProfileEntryWidget focusedProfile) {
        this.focusedProfile = focusedProfile;
    }

    void selectKeybind(ProfileEntryWidget profile) {
        if (this.keybindProfile != null) this.keybindProfile.setKeybindSelected(false);
        if (this.focusedProfile != null) this.focusedProfile.cancelEditing();

        this.keybindProfile = profile;
        this.keybindProfile.setKeybindSelected(true);
    }

    private void setSelectedKeybind(InputUtil.Key key) {
        ProfileEntryWidget profile = this.keybindProfile;
        if (profile == null) return;

        if (ConfigManager.setProfileKeybind(profile.profileName(), key.getTranslationKey())) {
            profile.setKeybind(key);
        } else {
            profile.setKeybindSelected(false);
        }
        this.keybindProfile = null;
    }

    void refreshProfiles() {
        this.clearAndInit();
    }
}
