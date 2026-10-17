package dev.ifair.luahud.api.modules;

import dev.ifair.luahud.api.LuaApiRegistrar;
import dev.ifair.luahud.api.LuaParameter;
import dev.ifair.luahud.api.LuaType;
import dev.ifair.luahud.compat.ChatText;
import dev.ifair.luahud.api.models.ChatTypes;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.text.Text;

import java.util.function.Consumer;
import java.util.function.Supplier;

/** Local chat output; this module never sends chat packets to the server. */
public final class ChatModule {
    private ChatModule() {}

    public static void register(LuaApiRegistrar api, MinecraftClient client) {
        register(api, text -> client.inGameHud.getChatHud().addMessage(text),
                () -> client.world == null ? null : client.world.getRegistryManager());
    }

    static void register(LuaApiRegistrar api, Consumer<Text> output,
                         Supplier<RegistryWrapper.WrapperLookup> registries) {
        LuaType<?> component = ChatTypes.text();
        var send = api.root().function("sendMessage", LuaType.nil(),
                "Add a message to local chat only; never sends it to the server. "
                        + "Strings are literal text. Tables use modern Minecraft text components "
                        + "with click_event and hover_event on every supported version.");
        LuaParameter<Object> message = send.parameter("message", LuaType.union(LuaType.string(), component),
                "Literal string or Minecraft text component table (text, color, extra, translate, click_event, hover_event, etc.)");
        send.call(arguments -> {
            output.accept(ChatText.decode(arguments.get(message), registries.get()));
            return null;
        });
    }
}
