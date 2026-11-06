package dev.ifair.luahud.api.modules;

import dev.ifair.luahud.api.LuaApiRegistrar;
import dev.ifair.luahud.api.LuaType;
import net.minecraft.client.option.AttackIndicator;
import net.minecraft.client.option.CloudRenderMode;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.GraphicsMode;
import net.minecraft.client.option.NarratorMode;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.client.render.ChunkBuilderMode;
import net.minecraft.network.message.ChatVisibility;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Arm;

//? if >=1.21.2 {
/*import net.minecraft.client.option.InactivityFpsLimit;
*///?}
//? if >=1.21.6 {
/*import net.minecraft.client.sound.MusicTracker.MusicFrequency;
*///?}
//? if >=1.21.11 {
/*import net.minecraft.client.option.MusicToastMode;
import net.minecraft.client.option.TextureFilteringMode;
*///?}
//? if <1.21.2 {
import net.minecraft.client.option.ParticlesMode;
//?} else {
/*import net.minecraft.particle.ParticlesMode;
*///?}

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.Supplier;

/** Read-only client options. The same catalogue drives reads, completions and typed overloads. */
public final class OptionsModule {
    private record Option<T>(LuaType<T> type, Function<GameOptions, T> read) {}

    private static final Map<String, Option<?>> OPTIONS = createOptions();
    private static final LuaType<Object> OPTION_VALUE = LuaType.union(
            LuaType.bool(), LuaType.integer(), LuaType.number(), LuaType.string());

    private OptionsModule() {}

    public static void register(LuaApiRegistrar api, Supplier<GameOptions> options) {
        var getOption = api.root().function("getOption", OPTION_VALUE,
                "Read the current Minecraft client setting. Unknown names raise an error. "
                        + "Numbers use the game's stored units (for example mouse sensitivity is 0–1); "
                        + "enum values are lowercase names with underscores. This does not change settings.");
        // Literal overloads provide both completions and precise return types; an alias
        // listing the same names would make language servers suggest each name twice.
        var name = getOption.parameter("name", LuaType.string(), "Client option name; available names depend on the Minecraft version");
        OPTIONS.forEach((key, option) -> getOption.overload(option.type(), LuaType.literal(key)));
        getOption.call(args -> {
            String key = args.get(name);
            Option<?> option = OPTIONS.get(key);
            if (option == null) throw new IllegalArgumentException("Unknown Minecraft option: " + key);
            GameOptions current = options.get();
            if (current == null) throw new IllegalStateException("Minecraft client options are unavailable");
            return option.read().apply(current);
        });
    }

    private static Map<String, Option<?>> createOptions() {
        Map<String, Option<?>> options = new TreeMap<>();
        option(options, "monochromeLogo", LuaType.bool(), GameOptions::getMonochromeLogo);
        option(options, "hideLightningFlashes", LuaType.bool(), GameOptions::getHideLightningFlashes);
        option(options, "hideSplashTexts", LuaType.bool(), GameOptions::getHideSplashTexts);
        option(options, "mouseSensitivity", LuaType.number(), GameOptions::getMouseSensitivity);
        option(options, "renderDistance", LuaType.integer(), GameOptions::getViewDistance);
        option(options, "simulationDistance", LuaType.integer(), GameOptions::getSimulationDistance);
        option(options, "entityDistanceScaling", LuaType.number(), GameOptions::getEntityDistanceScaling);
        option(options, "maxFps", LuaType.integer(), GameOptions::getMaxFps);
        enumOption(options, "cloudRenderMode", CloudRenderMode.class, GameOptions::getCloudRenderMode);
        //? if <1.21.11 {
        enumOption(options, "graphicsMode", GraphicsMode.class, GameOptions::getGraphicsMode);
        //?} else {
        /*enumOption(options, "graphicsMode", GraphicsMode.class, GameOptions::getPreset);
        *///?}
        option(options, "ao", LuaType.bool(), GameOptions::getAo);
        enumOption(options, "chunkBuilderMode", ChunkBuilderMode.class, GameOptions::getChunkBuilderMode);
        enumOption(options, "chatVisibility", ChatVisibility.class, GameOptions::getChatVisibility);
        option(options, "chatOpacity", LuaType.number(), GameOptions::getChatOpacity);
        option(options, "chatLineSpacing", LuaType.number(), GameOptions::getChatLineSpacing);
        option(options, "menuBackgroundBlurriness", LuaType.integer(), GameOptions::getMenuBackgroundBlurriness);
        option(options, "textBackgroundOpacity", LuaType.number(), GameOptions::getTextBackgroundOpacity);
        option(options, "panoramaSpeed", LuaType.number(), GameOptions::getPanoramaSpeed);
        option(options, "highContrast", LuaType.bool(), GameOptions::getHighContrast);
        option(options, "narratorHotkey", LuaType.bool(), GameOptions::getNarratorHotkey);
        enumOption(options, "mainArm", Arm.class, GameOptions::getMainArm);
        option(options, "chatScale", LuaType.number(), GameOptions::getChatScale);
        option(options, "chatWidth", LuaType.number(), GameOptions::getChatWidth);
        option(options, "chatHeightUnfocused", LuaType.number(), GameOptions::getChatHeightUnfocused);
        option(options, "chatHeightFocused", LuaType.number(), GameOptions::getChatHeightFocused);
        option(options, "chatDelay", LuaType.number(), GameOptions::getChatDelay);
        option(options, "notificationDisplayTime", LuaType.number(), GameOptions::getNotificationDisplayTime);
        option(options, "mipmapLevels", LuaType.integer(), GameOptions::getMipmapLevels);
        enumOption(options, "attackIndicator", AttackIndicator.class, GameOptions::getAttackIndicator);
        option(options, "biomeBlendRadius", LuaType.integer(), GameOptions::getBiomeBlendRadius);
        option(options, "mouseWheelSensitivity", LuaType.number(), GameOptions::getMouseWheelSensitivity);
        option(options, "rawMouseInput", LuaType.bool(), GameOptions::getRawMouseInput);
        option(options, "autoJump", LuaType.bool(), GameOptions::getAutoJump);
        option(options, "operatorItemsTab", LuaType.bool(), GameOptions::getOperatorItemsTab);
        option(options, "autoSuggestions", LuaType.bool(), GameOptions::getAutoSuggestions);
        option(options, "chatColors", LuaType.bool(), GameOptions::getChatColors);
        option(options, "chatLinks", LuaType.bool(), GameOptions::getChatLinks);
        option(options, "chatLinksPrompt", LuaType.bool(), GameOptions::getChatLinksPrompt);
        option(options, "enableVsync", LuaType.bool(), GameOptions::getEnableVsync);
        option(options, "entityShadows", LuaType.bool(), GameOptions::getEntityShadows);
        option(options, "forceUnicodeFont", LuaType.bool(), GameOptions::getForceUnicodeFont);
        option(options, "japaneseGlyphVariants", LuaType.bool(), GameOptions::getJapaneseGlyphVariants);
        //? if <1.21.9 {
        option(options, "invertYMouse", LuaType.bool(), GameOptions::getInvertYMouse);
        //?} else {
        /*option(options, "invertYMouse", LuaType.bool(), GameOptions::getInvertMouseY);
        *///?}
        option(options, "discreteMouseScroll", LuaType.bool(), GameOptions::getDiscreteMouseScroll);
        option(options, "realmsNotifications", LuaType.bool(), GameOptions::getRealmsNotifications);
        option(options, "allowServerListing", LuaType.bool(), GameOptions::getAllowServerListing);
        option(options, "reducedDebugInfo", LuaType.bool(), GameOptions::getReducedDebugInfo);
        option(options, "showSubtitles", LuaType.bool(), GameOptions::getShowSubtitles);
        option(options, "directionalAudio", LuaType.bool(), GameOptions::getDirectionalAudio);
        option(options, "backgroundForChatOnly", LuaType.bool(), GameOptions::getBackgroundForChatOnly);
        option(options, "touchscreen", LuaType.bool(), GameOptions::getTouchscreen);
        option(options, "fullscreen", LuaType.bool(), GameOptions::getFullscreen);
        option(options, "bobView", LuaType.bool(), GameOptions::getBobView);
        option(options, "sneakToggled", LuaType.bool(), GameOptions::getSneakToggled);
        option(options, "sprintToggled", LuaType.bool(), GameOptions::getSprintToggled);
        option(options, "hideMatchedNames", LuaType.bool(), GameOptions::getHideMatchedNames);
        option(options, "showAutosaveIndicator", LuaType.bool(), GameOptions::getShowAutosaveIndicator);
        option(options, "onlyShowSecureChat", LuaType.bool(), GameOptions::getOnlyShowSecureChat);
        option(options, "fov", LuaType.integer(), GameOptions::getFov);
        option(options, "telemetryOptInExtra", LuaType.bool(), GameOptions::getTelemetryOptInExtra);
        option(options, "distortionEffectScale", LuaType.number(), GameOptions::getDistortionEffectScale);
        option(options, "fovEffectScale", LuaType.number(), GameOptions::getFovEffectScale);
        option(options, "darknessEffectScale", LuaType.number(), GameOptions::getDarknessEffectScale);
        option(options, "glintSpeed", LuaType.number(), GameOptions::getGlintSpeed);
        option(options, "glintStrength", LuaType.number(), GameOptions::getGlintStrength);
        option(options, "damageTiltStrength", LuaType.number(), GameOptions::getDamageTiltStrength);
        option(options, "gamma", LuaType.number(), GameOptions::getGamma);
        option(options, "guiScale", LuaType.integer(), GameOptions::getGuiScale);
        enumOption(options, "particles", ParticlesMode.class, GameOptions::getParticles);
        enumOption(options, "narrator", NarratorMode.class, GameOptions::getNarrator);
        option(options, "soundDevice", LuaType.string(), GameOptions::getSoundDevice);
        //? if >=1.21.2 {
        /*option(options, "highContrastBlockOutline", LuaType.bool(), GameOptions::getHighContrastBlockOutline);
        option(options, "rotateWithMinecart", LuaType.bool(), GameOptions::getRotateWithMinecart);
        enumOption(options, "inactivityFpsLimit", InactivityFpsLimit.class, GameOptions::getInactivityFpsLimit);
        *///?}
        //? if >=1.21.6 && <1.21.11 {
        /*option(options, "showNowPlayingToast", LuaType.bool(), GameOptions::getShowNowPlayingToast);
        *///?}
        //? if >=1.21.6 {
        /*option(options, "cloudRenderDistance", LuaType.integer(), GameOptions::getCloudRenderDistance);
        enumOption(options, "musicFrequency", MusicFrequency.class, GameOptions::getMusicFrequency);
        *///?}
        //? if >=1.21.9 {
        /*option(options, "allowCursorChanges", LuaType.bool(), GameOptions::getAllowCursorChanges);
        option(options, "attackToggled", LuaType.bool(), GameOptions::getAttackToggled);
        option(options, "chatDrafts", LuaType.bool(), GameOptions::getChatDrafts);
        option(options, "invertMouseX", LuaType.bool(), GameOptions::getInvertMouseX);
        option(options, "useToggled", LuaType.bool(), GameOptions::getUseToggled);
        option(options, "sprintWindow", LuaType.integer(), GameOptions::getSprintWindow);
        *///?}
        //? if >=1.21.11 {
        /*option(options, "cutoutLeaves", LuaType.bool(), GameOptions::getCutoutLeaves);
        option(options, "improvedTransparency", LuaType.bool(), GameOptions::getImprovedTransparency);
        option(options, "vignette", LuaType.bool(), GameOptions::getVignette);
        option(options, "chunkFade", LuaType.number(), GameOptions::getChunkFade);
        option(options, "maxAnisotropy", LuaType.integer(), GameOptions::getMaxAnisotropy);
        option(options, "weatherRadius", LuaType.integer(), GameOptions::getWeatherRadius);
        enumOption(options, "musicToast", MusicToastMode.class, GameOptions::getMusicToast);
        enumOption(options, "textureFiltering", TextureFilteringMode.class, GameOptions::getTextureFiltering);
        *///?}

        add(options, "advancedItemTooltips", LuaType.bool(), value -> value.advancedItemTooltips);
        add(options, "hideServerAddress", LuaType.bool(), value -> value.hideServerAddress);
        add(options, "pauseOnLostFocus", LuaType.bool(), value -> value.pauseOnLostFocus);
        add(options, "hudHidden", LuaType.bool(), value -> value.hudHidden);
        add(options, "smoothCameraEnabled", LuaType.bool(), value -> value.smoothCameraEnabled);
        add(options, "language", LuaType.string(), value -> value.language);
        add(options, "overrideWidth", LuaType.integer(), value -> value.overrideWidth);
        add(options, "overrideHeight", LuaType.integer(), value -> value.overrideHeight);
        add(options, "skipMultiplayerWarning", LuaType.bool(), value -> value.skipMultiplayerWarning);
        add(options, "syncChunkWrites", LuaType.bool(), value -> value.syncChunkWrites);
        add(options, "perspective", enumType(Perspective.class), value -> enumName(value.getPerspective()));
        for (SoundCategory category : SoundCategory.values()) {
            option(options, "soundCategory_" + category.getName(), LuaType.number(), value -> value.getSoundVolumeOption(category));
        }
        return Collections.unmodifiableMap(options);
    }

    private static <T> void add(Map<String, Option<?>> options, String name, LuaType<T> type, Function<GameOptions, T> read) {
        if (options.putIfAbsent(name, new Option<>(type, read)) != null) {
            throw new IllegalArgumentException("Duplicate Minecraft option: " + name);
        }
    }

    private static <T> void option(Map<String, Option<?>> options, String name, LuaType<T> type,
                                    Function<GameOptions, SimpleOption<T>> read) {
        add(options, name, type, value -> read.apply(value).getValue());
    }

    private static <E extends Enum<E>> void enumOption(Map<String, Option<?>> options, String name, Class<E> type,
                                                        Function<GameOptions, SimpleOption<E>> read) {
        add(options, name, enumType(type), value -> enumName(read.apply(value).getValue()));
    }

    @SuppressWarnings("unchecked")
    private static <E extends Enum<E>> LuaType<String> enumType(Class<E> type) {
        List<LuaType<String>> values = Arrays.stream(type.getEnumConstants()).map(value -> LuaType.literal(enumName(value))).toList();
        return values.size() == 1 ? values.getFirst() : LuaType.union(values.toArray(LuaType[]::new));
    }

    private static String enumName(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }
}
