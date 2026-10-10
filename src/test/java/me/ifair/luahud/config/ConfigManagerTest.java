package me.ifair.luahud.config;

import com.google.gson.Gson;
import net.fabricmc.loader.api.FabricLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.time.Duration;
import java.util.function.BooleanSupplier;

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
        Files.createDirectories(ConfigManager.MOD_CONFIG_DIR);
        ConfigData initial = new ConfigData();
        initial.profiles.add(new ConfigData.ProfileData("First"));
        initial.profiles.add(new ConfigData.ProfileData("Second"));
        Files.writeString(ConfigManager.JSON_CONFIG, new Gson().toJson(initial));
        Files.writeString(ConfigManager.MOD_CONFIG_DIR.resolve("First.lua"), "first script");
        Files.writeString(ConfigManager.MOD_CONFIG_DIR.resolve("Second.lua"), "second script");
        ConfigManager.load();
        ConfigManager.consumeProfileReloadSignal();
        revision = ConfigManager.getProfileRevision();
        keybindRevision = ConfigManager.getProfileKeybindRevision();
        originalJson = Files.readString(ConfigManager.JSON_CONFIG);
    }

    @Test
    void failedRenameKeepsMemoryAndJsonUnchanged() throws Exception {
        Files.delete(ConfigManager.MOD_CONFIG_DIR.resolve("First.lua"));

        ConfigManager.renameProfile("First", "Renamed");

        assertUnchanged();
        assertEquals(originalJson, Files.readString(ConfigManager.JSON_CONFIG));
    }

    @Test
    void renameNeverOverwritesAnExistingScript() throws Exception {
        Path target = ConfigManager.MOD_CONFIG_DIR.resolve("Renamed.lua");
        Files.writeString(target, "unregistered script");

        ConfigManager.renameProfile("First", "Renamed");

        assertUnchanged();
        assertEquals("unregistered script", Files.readString(target));
        assertEquals("first script", Files.readString(ConfigManager.MOD_CONFIG_DIR.resolve("First.lua")));
    }

    @Test
    void failedJsonWriteRestoresRenamedScript() throws Exception {
        blockJsonWrite();

        ConfigManager.renameProfile("First", "Renamed");

        assertUnchanged();
        assertEquals("first script", Files.readString(ConfigManager.MOD_CONFIG_DIR.resolve("First.lua")));
        assertFalse(Files.exists(ConfigManager.MOD_CONFIG_DIR.resolve("Renamed.lua")));
    }

    @Test
    void failedDeleteKeepsProfileRegistered() throws Exception {
        Path script = ConfigManager.MOD_CONFIG_DIR.resolve("First.lua");
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
        assertEquals("first script", Files.readString(ConfigManager.MOD_CONFIG_DIR.resolve("First.lua")));
        try (var files = Files.list(ConfigManager.MOD_CONFIG_DIR)) {
            assertEquals(2, files.count());
        }
    }

    @Test
    void failedJsonWritesLeaveEverySettingAndRevisionUnchanged() throws Exception {
        blockJsonWrite();

        ConfigManager.setShowHiddenFiles(true);
        ConfigManager.setGenerateExampleProfiles(false);
        ConfigManager.setProfileActive("First", false);
        ConfigManager.setProfileHidden("First", true);
        ConfigManager.setProfileKeybind("First", "key.keyboard.a");
        ConfigManager.moveProfile("First", 1);
        ConfigManager.removeProfile("First");
        ConfigManager.addProfile("Third");

        assertUnchanged();
        assertFalse(ConfigManager.isShowHiddenFilesEnabled());
        assertTrue(ConfigManager.isGenerateExampleProfilesEnabled());
        ConfigData.ProfileData first = ConfigManager.getProfiles().getFirst();
        assertTrue(first.isActive());
        assertFalse(first.isHidden());
        assertEquals("key.keyboard.unknown", first.getKeybind());
        assertTrue(Files.exists(ConfigManager.JSON_CONFIG.resolve("blocker")));
    }

    @Test
    void successfulRenameCommitsBothFilesAndSignals() throws Exception {
        ConfigManager.renameProfile("First", "Second");

        assertEquals(List.of("Second (1)", "Second"), names());
        assertFalse(Files.exists(ConfigManager.MOD_CONFIG_DIR.resolve("First.lua")));
        assertEquals("first script", Files.readString(ConfigManager.MOD_CONFIG_DIR.resolve("Second (1).lua")));
        assertCommittedProfilesAndSignals();
    }

    @Test
    void successfulDeleteCommitsBothFilesAndSignals() throws Exception {
        ConfigManager.deleteProfile("First");

        assertEquals(List.of("Second"), names());
        assertFalse(Files.exists(ConfigManager.MOD_CONFIG_DIR.resolve("First.lua")));
        assertCommittedProfilesAndSignals();
        try (var files = Files.list(ConfigManager.MOD_CONFIG_DIR)) {
            assertEquals(1, files.count());
        }
    }

    @Test
    void missingScriptCanStillBeRemovedFromConfig() throws Exception {
        Files.delete(ConfigManager.MOD_CONFIG_DIR.resolve("First.lua"));

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
        Files.writeString(ConfigManager.MOD_CONFIG_DIR.resolve("New Profile.lua"), "unregistered");

        ConfigManager.createProfile("New Profile");

        assertEquals(List.of("First", "Second", "New Profile (1)"), names());
        Path script = ConfigManager.MOD_CONFIG_DIR.resolve("New Profile (1).lua");
        assertTrue(Files.isRegularFile(script));
        assertEquals("", Files.readString(script));
        assertCommittedProfilesAndSignals();
    }

    @Test
    void failedJsonWriteRemovesCreatedScript() throws Exception {
        blockJsonWrite();

        ConfigManager.createProfile("New Profile");

        assertUnchanged();
        assertFalse(Files.exists(ConfigManager.MOD_CONFIG_DIR.resolve("New Profile.lua")));
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
        assertEquals("first script", Files.readString(ConfigManager.MOD_CONFIG_DIR.resolve("First.lua")));
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
            Path script = ConfigManager.MOD_CONFIG_DIR.resolve("First.lua");
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
        Files.createDirectory(ConfigManager.MOD_CONFIG_DIR.resolve("Directory.lua"));
        FileWatcher watcher = new FileWatcher();
        watcher.start();
        try {
            assertFalse(ConfigManager.hasProfile("Directory"));
            Files.writeString(ConfigManager.MOD_CONFIG_DIR.resolve("Third.lua"), "third script");
            await(() -> ConfigManager.hasProfile("Third"));
            Files.delete(ConfigManager.MOD_CONFIG_DIR.resolve("First.lua"));
            await(() -> !ConfigManager.hasProfile("First"));

            assertEquals(List.of("Second", "Third"), names());
        } finally {
            watcher.stop();
        }
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
