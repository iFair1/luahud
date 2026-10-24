package dev.ifair.luahud.command;

import com.mojang.brigadier.CommandDispatcher;
import dev.ifair.luahud.core.Engine;
import dev.ifair.luahud.error.ErrorManager;
import dev.ifair.luahud.gui.GuiUtils;
import dev.ifair.luahud.gui.LuaHUDErrorsScreen;
import dev.ifair.luahud.gui.LuaHUDScreen;
import dev.ifair.luahud.gui.components.HeaderWidget;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import static com.mojang.brigadier.arguments.LongArgumentType.getLong;
import static com.mojang.brigadier.arguments.LongArgumentType.longArg;
import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.StringArgumentType.greedyString;
import static com.mojang.brigadier.arguments.StringArgumentType.string;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public final class LuaHUDCommands {
    private LuaHUDCommands() {
    }

    public static void register(Engine engine) {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> register(dispatcher, engine));
    }

    static void register(CommandDispatcher<FabricClientCommandSource> dispatcher, Engine engine) {
        dispatcher.register(
                literal("luahud")
                        .executes(context -> openMenu(context.getSource().getClient()))
                        .then(literal("menu")
                                .executes(context -> openMenu(context.getSource().getClient())))
                        .then(literal("errors")
                                .executes(context -> openErrors(context.getSource().getClient(), null))
                                .then(argument("entry", longArg(1))
                                        .executes(context -> openErrors(context.getSource().getClient(),
                                                getLong(context, "entry")))))
                        .then(literal("reload")
                                .executes(context -> reload(context.getSource(), engine)))
                        .then(literal("help")
                                .executes(context -> GuiUtils.openInBrowser(HeaderWidget.DOCS_URL)))
                        .then(literal("report")
                                .then(literal("system")
                                        .then(argument("phase", string())
                                                .then(argument("message", greedyString())
                                                        .executes(context -> report(null,
                                                                getString(context, "phase"),
                                                                getString(context, "message"))))))
                                .then(literal("profile")
                                        .then(argument("profile", string())
                                                .then(argument("phase", string())
                                                        .then(argument("message", greedyString())
                                                                .executes(context -> report(
                                                                        getString(context, "profile"),
                                                                        getString(context, "phase"),
                                                                        getString(context, "message"))))))))
        );
    }

    private static int openMenu(MinecraftClient client) {
        client.send(() -> client.setScreen(new LuaHUDScreen(null)));
        return 1;
    }

    private static int openErrors(MinecraftClient client, Long entryId) {
        client.send(() -> client.setScreen(new LuaHUDErrorsScreen(null, entryId)));
        return 1;
    }

    private static int reload(FabricClientCommandSource source, Engine engine) {
        engine.reloadAll();
        if (!engine.isReady()) {
            source.sendError(Text.literal("[LuaHUD] Runtime could not be started. See /luahud errors."));
            return 0;
        }
        source.sendFeedback(Text.literal("[LuaHUD] Active profiles reloaded."));
        return 1;
    }

    private static int report(String profile, String phase, String message) {
        if (profile == null) {
            ErrorManager.reportSystem(phase, message);
        } else {
            ErrorManager.reportProfile(profile, phase, message);
        }
        return 1;
    }
}
