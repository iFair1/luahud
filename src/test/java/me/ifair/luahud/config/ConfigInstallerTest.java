package me.ifair.luahud.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigInstallerTest {
    private static final String EXAMPLE_PROFILE = "/assets/luahud/lua/Example Profile.lua";

    @TempDir
    Path tempDir;

    @Test
    void resourceIsCopiedWhenTargetDoesNotExist() throws Exception {
        ConfigInstaller.copyFromResourceIfAbsent(EXAMPLE_PROFILE, tempDir);

        assertTrue(Files.size(tempDir.resolve("Example Profile.lua")) > 0);
    }

    @Test
    void existingProfileIsNotOverwritten() throws Exception {
        Path profile = tempDir.resolve("Example Profile.lua");
        Files.writeString(profile, "user changes");

        ConfigInstaller.copyFromResourceIfAbsent(EXAMPLE_PROFILE, tempDir);

        assertEquals("user changes", Files.readString(profile));
    }
}
