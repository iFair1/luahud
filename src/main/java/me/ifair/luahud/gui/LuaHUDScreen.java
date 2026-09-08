package me.ifair.luahud.gui;

import me.ifair.luahud.LuaHUD;
import me.ifair.luahud.config.ConfigData;
import me.ifair.luahud.config.ConfigManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

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

    private static final int CONTENT_MAX_WIDTH = 540;
    private static final int BUTTON_HEIGHT = 20;
    private static final int ROW_HEIGHT = 20;

    private final Screen parent;
    private List<ProfileEntry> profiles = List.of();
    private int scrollOffset;
    private int contentLeft;
    private int contentWidth;
    private int listTop;
    private int listBottom;
    private int visibleRows;

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
        this.visibleRows = Math.max(1, (this.listBottom - this.listTop - 8) / ROW_HEIGHT);

        reloadProfiles();

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
        int footerButtonWidth = Math.min(150, (this.contentWidth - 10) / 2);
        int footerWidth = footerButtonWidth * 2 + 10;
        int footerX = (this.width - footerWidth) / 2;
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Open Folder"), button -> openProfilesFolder())
                .dimensions(footerX, footerY, footerButtonWidth, BUTTON_HEIGHT)
                .build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Done"), button -> this.close())
                .dimensions(footerX + footerButtonWidth + 10, footerY, footerButtonWidth, BUTTON_HEIGHT)
                .build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);

        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 26, 0xFFFFFF);
        context.fill(this.contentLeft, this.listTop, this.contentLeft + this.contentWidth, this.listBottom, 0x88000000);
        context.drawBorder(this.contentLeft, this.listTop, this.contentWidth, this.listBottom - this.listTop, 0xFF555555);

        renderProfiles(context);
        super.render(context, mouseX, mouseY, delta);
    }

    private void renderProfiles(DrawContext context) {
        if (this.profiles.isEmpty()) {
            context.drawCenteredTextWithShadow(
                    this.textRenderer,
                    Text.literal("No Lua profiles found"),
                    this.width / 2,
                    this.listTop + (this.listBottom - this.listTop - this.textRenderer.fontHeight) / 2,
                    0xAAAAAA
            );
            return;
        }

        int rowLeft = this.contentLeft + 5;
        int rowWidth = this.contentWidth - 10;
        int rowTextWidth = Math.max(20, rowWidth - 100);

        context.enableScissor(this.contentLeft + 1, this.listTop + 1, this.contentLeft + this.contentWidth - 1, this.listBottom - 1);
        for (int row = 0; row < this.visibleRows; row++) {
            int profileIndex = this.scrollOffset + row;
            if (profileIndex >= this.profiles.size()) break;

            ProfileEntry profile = this.profiles.get(profileIndex);
            int rowTop = this.listTop + 4 + row * ROW_HEIGHT;
            int background = row % 2 == 0 ? 0x44000000 : 0x22000000;
            context.fill(rowLeft, rowTop, rowLeft + rowWidth, rowTop + ROW_HEIGHT - 2, background);

            int textY = rowTop + (ROW_HEIGHT - 2 - this.textRenderer.fontHeight) / 2;
            int statusColor = profile.data().active ? 0x55FF55 : 0xFF5555;
            context.drawTextWithShadow(this.textRenderer, profile.data().active ? "On" : "Off", rowLeft + 5, textY, statusColor);

            String profileName = this.textRenderer.trimToWidth(profile.name(), rowTextWidth);
            context.drawTextWithShadow(this.textRenderer, profileName, rowLeft + 35, textY, 0xFFFFFF);
            if (profile.data().hidden) {
                context.drawTextWithShadow(this.textRenderer, "Hidden", rowLeft + rowWidth - 45, textY, 0xAAAAAA);
            }
        }
        context.disableScissor();

        if (this.profiles.size() > this.visibleRows) {
            int trackTop = this.listTop + 4;
            int trackHeight = this.listBottom - this.listTop - 8;
            int thumbHeight = Math.max(12, trackHeight * this.visibleRows / this.profiles.size());
            int maxOffset = this.profiles.size() - this.visibleRows;
            int thumbTravel = trackHeight - thumbHeight;
            int thumbTop = trackTop + thumbTravel * this.scrollOffset / maxOffset;
            int scrollbarX = this.contentLeft + this.contentWidth - 4;
            context.fill(scrollbarX, trackTop, scrollbarX + 2, trackTop + trackHeight, 0xFF333333);
            context.fill(scrollbarX, thumbTop, scrollbarX + 2, thumbTop + thumbHeight, 0xFFAAAAAA);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (mouseX >= this.contentLeft && mouseX < this.contentLeft + this.contentWidth
                && mouseY >= this.listTop && mouseY < this.listBottom
                && this.profiles.size() > this.visibleRows) {
            int maxOffset = this.profiles.size() - this.visibleRows;
            this.scrollOffset = Math.clamp(this.scrollOffset - (int) Math.signum(verticalAmount), 0, maxOffset);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(this.parent);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private void reloadProfiles() {
        boolean showHidden = ConfigManager.isShowHiddenFilesEnabled();
        this.profiles = ConfigManager.getProfiles().entrySet().stream()
                .filter(entry -> showHidden || !entry.getValue().hidden)
                .sorted(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER))
                .map(entry -> new ProfileEntry(entry.getKey(), entry.getValue()))
                .toList();
        this.scrollOffset = Math.clamp(this.scrollOffset, 0, Math.max(0, this.profiles.size() - this.visibleRows));
    }

    private static void openProfilesFolder() {
        try {
            Files.createDirectories(PROFILES_DIR);
            Util.getOperatingSystem().open(PROFILES_DIR);
        } catch (IOException e) {
            LuaHUD.LOGGER.error("Error while opening profiles directory at {}: {}", PROFILES_DIR, e.getMessage());
        }
    }

    private static void openInBrowser(String url) {
        try {
            Util.getOperatingSystem().open(new URI(url));
        } catch (Exception e) {
            LuaHUD.LOGGER.error("There was an error while directing to url \"{}\": {}", url, e.getMessage());
        }
    }

    private record ProfileEntry(String name, ConfigData.ProfileData data) {
    }
}
