package me.ifair.luahud.config;

import com.google.gson.Gson;
import net.fabricmc.loader.api.FabricLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

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
