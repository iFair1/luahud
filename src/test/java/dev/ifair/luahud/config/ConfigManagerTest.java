package dev.ifair.luahud.config;

import com.google.gson.Gson;
import net.fabricmc.loader.api.FabricLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.time.Duration;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class ConfigManagerTest {
    @TempDir
    static Path directory;

    private long revision;
    private long keybindRevision;
    private String originalJson;

    @BeforeAll
    static void configureLoaderDirectory() throws Exception {
        // Point Fabric's config directory at the fixture before ConfigManager initializes.
        Object loader = FabricLoader.getInstance();
        Field configDir = loader.getClass().getDeclaredField("configDir");
        configDir.setAccessible(true);
        configDir.set(loader, directory);
    }

    @BeforeEach
    void loadProfiles() throws Exception {
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                if (!path.equals(directory)) Files.delete(path);
            }
        }
        Files.createDirectories(ConfigManager.PROFILES_DIR);
        ConfigData initial = new ConfigData();
        initial.profiles.add(new ConfigData.ProfileData("First"));
        initial.profiles.add(new ConfigData.ProfileData("Second"));
        Files.writeString(ConfigManager.JSON_CONFIG, new Gson().toJson(initial));
        Files.writeString(ConfigManager.PROFILES_DIR.resolve("First.lua"), "first script");
        Files.writeString(ConfigManager.PROFILES_DIR.resolve("Second.lua"), "second script");
        ConfigManager.load();
        ConfigManager.consumeProfileReloadSignal();
        revision = ConfigManager.getProfileRevision();
        keybindRevision = ConfigManager.getProfileKeybindRevision();
        originalJson = Files.readString(ConfigManager.JSON_CONFIG);
    }

    @Test
    void defaultsUseSeparateProfileAndApiDirectoriesWithRootEditorSettings() throws Exception {
        ConfigInstaller.generateDefaults();

        assertEquals(ConfigManager.MOD_CONFIG_DIR.resolve("profiles"), ConfigManager.getScriptsDir());
        assertEquals(ConfigManager.MOD_CONFIG_DIR.resolve("api"), ConfigManager.getApiDir());
        assertTrue(Files.isDirectory(ConfigManager.getApiDir()));
        assertTrue(Files.isRegularFile(ConfigManager.getScriptsDir().resolve("Welcome Profile.lua")));
        assertFalse(Files.exists(ConfigManager.MOD_CONFIG_DIR.resolve("Welcome Profile.lua")));
        assertFalse(Files.exists(ConfigManager.getApiDir().resolve(".luarc.json")));
        assertFalse(Files.exists(ConfigManager.MOD_CONFIG_DIR.resolve("system")));
        String settings = Files.readString(ConfigManager.MOD_CONFIG_DIR.resolve(".luarc.json"));
        assertEquals("./api", com.google.gson.JsonParser.parseString(settings).getAsJsonObject()
                .getAsJsonArray("Lua.workspace.library").get(0).getAsString());

        Files.writeString(ConfigManager.MOD_CONFIG_DIR.resolve(".luarc.json"), "custom settings");
        Files.writeString(ConfigManager.getScriptsDir().resolve("Welcome Profile.lua"), "custom profile");
        ConfigInstaller.generateDefaults();

        assertEquals("custom settings", Files.readString(ConfigManager.MOD_CONFIG_DIR.resolve(".luarc.json")));
        assertEquals("custom profile", Files.readString(ConfigManager.getScriptsDir().resolve("Welcome Profile.lua")));
    }

    @Test
    void watcherOnlyDiscoversProfilesInsideProfilesDirectory() throws Exception {
        Files.writeString(ConfigManager.MOD_CONFIG_DIR.resolve("Root.lua"), "root script");
        Files.createDirectories(ConfigManager.getApiDir());
        Files.writeString(ConfigManager.getApiDir().resolve("lh.lua"), "api definitions");
        FileWatcher watcher = new FileWatcher();
        watcher.start();
        try {
            assertEquals(List.of("First", "Second"), names());
            Files.writeString(ConfigManager.MOD_CONFIG_DIR.resolve("Another Root.lua"), "root script");
            Files.writeString(ConfigManager.getApiDir().resolve("addon.lua"), "addon definitions");
            Files.writeString(ConfigManager.getScriptsDir().resolve("Third.lua"), "third script");
            await(() -> ConfigManager.hasProfile("Third"));

            assertEquals(List.of("First", "Second", "Third"), names());
        } finally {
            watcher.stop();
        }
    }

    @Test
    void failedRenameKeepsMemoryAndJsonUnchanged() throws Exception {
        Files.delete(ConfigManager.PROFILES_DIR.resolve("First.lua"));

        ConfigManager.renameProfile("First", "Renamed");

        assertUnchanged();
        assertEquals(originalJson, Files.readString(ConfigManager.JSON_CONFIG));
    }

    @Test
    void renameNeverOverwritesAnExistingScript() throws Exception {
        Path target = ConfigManager.PROFILES_DIR.resolve("Renamed.lua");
        Files.writeString(target, "unregistered script");

        ConfigManager.renameProfile("First", "Renamed");

        assertUnchanged();
        assertEquals("unregistered script", Files.readString(target));
        assertEquals("first script", Files.readString(ConfigManager.PROFILES_DIR.resolve("First.lua")));
    }

    @Test
    void failedJsonWriteRestoresRenamedScript() throws Exception {
        blockJsonWrite();

        ConfigManager.renameProfile("First", "Renamed");

        assertUnchanged();
        assertEquals("first script", Files.readString(ConfigManager.PROFILES_DIR.resolve("First.lua")));
        assertFalse(Files.exists(ConfigManager.PROFILES_DIR.resolve("Renamed.lua")));
    }

    @Test
    void failedDeleteKeepsProfileRegistered() throws Exception {
        Path script = ConfigManager.PROFILES_DIR.resolve("First.lua");
        Files.delete(script);
        Files.createDirectory(script);
        Files.writeString(script.resolve("child"), "cannot delete as a file");

        ConfigManager.deleteProfile("First");

        assertUnchanged();
        assertEquals(originalJson, Files.readString(ConfigManager.JSON_CONFIG));
        assertTrue(Files.exists(script.resolve("child")));
    }

    @Test
    void failedJsonWriteRestoresDeletedScript() throws Exception {
        blockJsonWrite();

        ConfigManager.deleteProfile("First");

        assertUnchanged();
        assertEquals("first script", Files.readString(ConfigManager.PROFILES_DIR.resolve("First.lua")));
        try (var files = Files.list(ConfigManager.PROFILES_DIR)) {
            assertEquals(2, files.count());
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("settingChanges")
    void failedJsonWriteLeavesSettingsAndRevisionsUnchanged(String name, Runnable change) throws Exception {
        blockJsonWrite();

        change.run();

        assertUnchanged();
        assertFalse(ConfigManager.isShowHiddenFilesEnabled());
        assertTrue(ConfigManager.isGenerateExampleProfilesEnabled());
        ConfigData.ProfileData first = ConfigManager.getProfiles().getFirst();
        assertTrue(first.isActive());
        assertFalse(first.isHidden());
        assertEquals("key.keyboard.unknown", first.getKeybind());
        assertTrue(Files.exists(ConfigManager.JSON_CONFIG.resolve("blocker")));
    }

    private static Stream<Arguments> settingChanges() {
        return Stream.of(
                settingChange("show hidden files", () -> ConfigManager.setShowHiddenFiles(true)),
                settingChange("example profiles", () -> ConfigManager.setGenerateExampleProfiles(false)),
                settingChange("active profile", () -> ConfigManager.setProfileActive("First", false)),
                settingChange("hidden profile", () -> ConfigManager.setProfileHidden("First", true)),
                settingChange("profile keybind", () -> ConfigManager.setProfileKeybind("First", "key.keyboard.a")),
                settingChange("profile order", () -> ConfigManager.moveProfile("First", 1)),
                settingChange("remove profile", () -> ConfigManager.removeProfile("First")),
                settingChange("add profile", () -> ConfigManager.addProfile("Third")));
    }

    private static Arguments settingChange(String name, Runnable change) {
        return Arguments.of(name, change);
    }

    @Test
    void successfulRenameCommitsBothFilesAndSignals() throws Exception {
        ConfigManager.renameProfile("First", "Second");

        assertEquals(List.of("Second (1)", "Second"), names());
        assertFalse(Files.exists(ConfigManager.PROFILES_DIR.resolve("First.lua")));
        assertEquals("first script", Files.readString(ConfigManager.PROFILES_DIR.resolve("Second (1).lua")));
        assertCommittedProfilesAndSignals();
    }

    @Test
    void successfulDeleteCommitsBothFilesAndSignals() throws Exception {
        ConfigManager.deleteProfile("First");

        assertEquals(List.of("Second"), names());
        assertFalse(Files.exists(ConfigManager.PROFILES_DIR.resolve("First.lua")));
        assertCommittedProfilesAndSignals();
        try (var files = Files.list(ConfigManager.PROFILES_DIR)) {
            assertEquals(1, files.count());
        }
    }

    @Test
    void missingScriptCanStillBeRemovedFromConfig() throws Exception {
        Files.delete(ConfigManager.PROFILES_DIR.resolve("First.lua"));

        ConfigManager.deleteProfile("First");

        assertEquals(List.of("Second"), names());
        assertCommittedProfilesAndSignals();
    }

    @Test
    void addedProfileUsesUniqueNameAndDoesNotRetainCallerReference() throws Exception {
        ConfigData.ProfileData supplied = new ConfigData.ProfileData("Unrelated");
        ConfigManager.addProfile("First", supplied);
        supplied.setName("Mutated");
        supplied.setActive(false);

        assertEquals(List.of("First", "Second", "First (1)"), names());
        assertTrue(ConfigManager.getProfiles().getLast().isActive());
        assertCommittedProfilesAndSignals();
    }

    @Test
    void createdProfileHasUniqueNameAndEmptyScript() throws Exception {
        Files.writeString(ConfigManager.PROFILES_DIR.resolve("New Profile.lua"), "unregistered");

        ConfigManager.createProfile("New Profile");

        assertEquals(List.of("First", "Second", "New Profile (1)"), names());
        Path script = ConfigManager.PROFILES_DIR.resolve("New Profile (1).lua");
        assertTrue(Files.isRegularFile(script));
        assertEquals("", Files.readString(script));
        assertCommittedProfilesAndSignals();
    }

    @Test
    void failedJsonWriteRemovesCreatedScript() throws Exception {
        blockJsonWrite();

        ConfigManager.createProfile("New Profile");

        assertUnchanged();
        assertFalse(Files.exists(ConfigManager.PROFILES_DIR.resolve("New Profile.lua")));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", ".", "..", "../Outside", "nested/Profile", "..\\Outside", "/tmp/Outside", "nul\u0000name"})
    void invalidNamesNeverChangeFilesOrConfiguration(String name) throws Exception {
        ConfigManager.createProfile(name);
        ConfigManager.addProfile(name);
        ConfigManager.renameProfile("First", name);

        assertUnchanged();
        assertEquals(originalJson, Files.readString(ConfigManager.JSON_CONFIG));
        assertEquals("first script", Files.readString(ConfigManager.PROFILES_DIR.resolve("First.lua")));
    }

    @Test
    void largeNumericSuffixCanBeIncrementedWithoutOverflow() {
        ConfigManager.addProfile("Profile (2147483647)");
        ConfigManager.addProfile("Profile (2147483647)");
        ConfigManager.addProfile("Profile (99999999999999999999)");
        ConfigManager.addProfile("Profile (99999999999999999999)");

        assertTrue(ConfigManager.hasProfile("Profile (2147483648)"));
        assertTrue(ConfigManager.hasProfile("Profile (100000000000000000000)"));
    }

    @Test
    void loadingNormalizesUnsafeNamesAndInvalidKeybindsOnDisk() throws Exception {
        ConfigData initial = new ConfigData();
        initial.profiles.add(new ConfigData.ProfileData("../Outside"));
        ConfigData.ProfileData valid = new ConfigData.ProfileData("First");
        valid.setKeybind("key.keyboard.invalid");
        initial.profiles.add(valid);
        Files.writeString(ConfigManager.JSON_CONFIG, new Gson().toJson(initial));

        ConfigManager.load();

        assertEquals(List.of("First"), names());
        assertEquals("key.keyboard.unknown", ConfigManager.getProfiles().getFirst().getKeybind());
        ConfigData saved = new Gson().fromJson(Files.readString(ConfigManager.JSON_CONFIG), ConfigData.class);
        assertEquals(1, saved.profiles.size());
        assertEquals("key.keyboard.unknown", saved.profiles.getFirst().getKeybind());
    }

    @Test
    void editorSavePreservesProfileSettingsAndOrder() throws Exception {
        ConfigManager.setProfileActive("First", false);
        ConfigManager.setProfileHidden("First", true);
        ConfigManager.setProfileKeybind("First", "key.keyboard.a");
        ConfigManager.consumeProfileReloadSignal();
        long beforeSave = ConfigManager.getProfileRevision();
        FileWatcher watcher = new FileWatcher();
        watcher.start();
        try {
            Path script = ConfigManager.PROFILES_DIR.resolve("First.lua");
            Files.delete(script);
            Thread.sleep(50); // A save may span multiple WatchService event batches.
            Files.writeString(script, "updated script");
            await(watcher::consumeReloadSignal);

            assertEquals(List.of("First", "Second"), names());
            ConfigData.ProfileData first = ConfigManager.getProfiles().getFirst();
            assertFalse(first.isActive());
            assertTrue(first.isHidden());
            assertEquals("key.keyboard.a", first.getKeybind());
            assertEquals(beforeSave, ConfigManager.getProfileRevision());
        } finally {
            watcher.stop();
        }
    }

    @Test
    void watcherDetectsRealCreationAndDeletionButIgnoresDirectories() throws Exception {
        Files.createDirectory(ConfigManager.PROFILES_DIR.resolve("Directory.lua"));
        FileWatcher watcher = new FileWatcher();
        watcher.start();
        try {
            assertFalse(ConfigManager.hasProfile("Directory"));
            Files.writeString(ConfigManager.PROFILES_DIR.resolve("Third.lua"), "third script");
            await(() -> ConfigManager.hasProfile("Third"));
            Files.delete(ConfigManager.PROFILES_DIR.resolve("First.lua"));
            await(() -> !ConfigManager.hasProfile("First"));

            assertEquals(List.of("Second", "Third"), names());
        } finally {
            watcher.stop();
        }
    }

    @Test void durationClockIsPersistedWhenMissingAndPreservedByOtherSettingChanges() throws Exception {
        Files.writeString(ConfigManager.JSON_CONFIG, "{}");
        ConfigManager.load();
        assertEquals("realTime", ConfigManager.getSectionDurationClock());
        assertTrue(Files.readString(ConfigManager.JSON_CONFIG).contains("sectionDurationClock"));
        Files.writeString(ConfigManager.JSON_CONFIG, "{\"sectionDurationClock\":\"gameTime\"}");
        ConfigManager.load();
        ConfigManager.setShowHiddenFiles(true);
        ConfigData saved = new Gson().fromJson(Files.readString(ConfigManager.JSON_CONFIG), ConfigData.class);
        assertEquals("gameTime", saved.sectionDurationClock);
    }

    private static void await(BooleanSupplier condition) {
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            while (!condition.getAsBoolean()) Thread.sleep(10);
        });
    }

    private void blockJsonWrite() throws Exception {
        // A nonempty directory fails replacement even when tests run as root.
        Files.delete(ConfigManager.JSON_CONFIG);
        Files.createDirectory(ConfigManager.JSON_CONFIG);
        Files.writeString(ConfigManager.JSON_CONFIG.resolve("blocker"), "preserve");
    }

    private List<String> names() {
        return ConfigManager.getProfiles().stream().map(ConfigData.ProfileData::getName).toList();
    }

    private void assertUnchanged() {
        assertEquals(List.of("First", "Second"), names());
        assertEquals(revision, ConfigManager.getProfileRevision());
        assertEquals(keybindRevision, ConfigManager.getProfileKeybindRevision());
        assertFalse(ConfigManager.consumeProfileReloadSignal());
    }

    private void assertCommittedProfilesAndSignals() throws Exception {
        ConfigData saved = new Gson().fromJson(Files.readString(ConfigManager.JSON_CONFIG), ConfigData.class);
        assertEquals(names(), saved.profiles.stream().map(ConfigData.ProfileData::getName).toList());
        assertEquals(revision + 1, ConfigManager.getProfileRevision());
        assertEquals(keybindRevision + 1, ConfigManager.getProfileKeybindRevision());
        assertTrue(ConfigManager.consumeProfileReloadSignal());
        assertFalse(ConfigManager.consumeProfileReloadSignal());
    }
}
