package me.ifair.luahud.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import me.ifair.luahud.error.ErrorEntry;
import me.ifair.luahud.error.ErrorManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LuaHUDCommandsTest {
    private CommandDispatcher<FabricClientCommandSource> dispatcher;

    @BeforeEach
    void setup() {
        ErrorManager.beginProfileReload();
        dispatcher = new CommandDispatcher<>();
        LuaHUDCommands.register(dispatcher, null);
    }

    @Test
    void systemReportPreservesQuotedPhaseAndMessageWithSpaces() throws Exception {
        assertEquals(1, dispatcher.execute("luahud report system \"command test\" Test system error", null));

        ErrorEntry error = ErrorManager.snapshot().stream()
                .filter(entry -> entry.system() && entry.phase().equals("command test"))
                .findFirst().orElseThrow();
        assertEquals("Test system error", error.message());
        assertTrue(ErrorManager.drainNotifications().stream().anyMatch(entry -> entry.id() == error.id()));
    }

    @Test
    void profileReportUsesNormalDuplicateCountingAndReloadClearing() throws Exception {
        String command = "luahud report profile \"My Profile\" tick My Profile.lua:12: Test profile error";
        dispatcher.execute(command, null);
        dispatcher.execute(command, null);

        ErrorEntry error = ErrorManager.snapshot().stream().filter(entry -> !entry.system()).findFirst().orElseThrow();
        assertEquals("My Profile", error.group());
        assertEquals("tick", error.phase());
        assertEquals("My Profile.lua:12: Test profile error", error.message());
        assertEquals("12", error.line());
        assertEquals(2, error.count());

        ErrorManager.beginProfileReload();
        assertTrue(ErrorManager.find(error.id()).isEmpty());
    }

    @Test
    void incompleteReportDoesNotCreateAnError() {
        int before = ErrorManager.distinctCount();
        assertThrows(CommandSyntaxException.class,
                () -> dispatcher.execute("luahud report system tick", null));
        assertThrows(CommandSyntaxException.class,
                () -> dispatcher.execute("luahud report profile Example tick", null));
        assertEquals(before, ErrorManager.distinctCount());
    }
}
