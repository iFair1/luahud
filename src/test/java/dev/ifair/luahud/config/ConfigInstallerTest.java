package dev.ifair.luahud.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ConfigInstallerTest {
    private static final String EXAMPLE_PROFILE = "/assets/luahud/lua/Welcome Profile.lua";

    @TempDir
    Path tempDir;

    @Test
    void resourceIsCopiedWhenTargetDoesNotExist() throws Exception {
        ConfigInstaller.copyFromResourceIfAbsent(EXAMPLE_PROFILE, tempDir);

        try (var resource = ConfigInstaller.class.getResourceAsStream(EXAMPLE_PROFILE)) {
            assertNotNull(resource);
            assertArrayEquals(resource.readAllBytes(), Files.readAllBytes(tempDir.resolve("Welcome Profile.lua")));
        }
    }

    @Test
    void missingBundledResourceFailsWithoutCreatingAFile() throws Exception {
        assertThrows(IllegalStateException.class,
                () -> ConfigInstaller.copyFromResourceIfAbsent("/assets/luahud/lua/missing.lua", tempDir));

        try (var files = Files.list(tempDir)) {
            assertEquals(0, files.count());
        }
    }

    @Test
    void existingProfileIsNotOverwritten() throws Exception {
        Path profile = tempDir.resolve("Welcome Profile.lua");
        Files.writeString(profile, "user changes");

        ConfigInstaller.copyFromResourceIfAbsent(EXAMPLE_PROFILE, tempDir);

        assertEquals("user changes", Files.readString(profile));
    }
}
