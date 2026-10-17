package dev.ifair.luahud.api.modules;

import dev.ifair.luahud.api.core.ApiRegistry;
import dev.ifair.luahud.api.core.StubGenerator;
import dev.ifair.luahud.api.core.DocumentationGenerator;
import com.google.gson.JsonParser;
import dev.ifair.luahud.core.CobaltRuntime;
import net.minecraft.text.Text;
import org.junit.jupiter.api.Test;
import org.squiddev.cobalt.LuaError;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChatModuleTest {
    private final List<Text> messages = new ArrayList<>();

    private ApiRegistry registry() {
        ApiRegistry registry = new ApiRegistry(ignored -> {});
        ChatModule.register(registry.registrar("luahud"), messages::add, () -> null);
        registry.freeze();
        return registry;
    }

    private CobaltRuntime runtime() throws LuaError {
        CobaltRuntime runtime = new CobaltRuntime(registry());
        runtime.start();
        return runtime;
    }

    @Test void stringsRemainLiteralIncludingJsonLookingTextAndSectionCodes() throws Exception {
        runtime().execute("lh.sendMessage('§aMerhaba'); lh.sendMessage('{\"text\":\"literal\"}')", "literal-chat");
        assertEquals("§aMerhaba", messages.getFirst().getString());
        assertEquals("{\"text\":\"literal\"}", messages.get(1).getString());
    }

    @Test void tablesPreserveNestedComponentsColorsAndStyleOverrides() throws Exception {
        runtime().execute("""
                lh.sendMessage({text='[LuaHUD] ', color='#12AB34', bold=true, extra={
                    {text='Hazır', bold=false, italic=true},
                    {text='!', color='gold'}
                }})
                """, "styled-chat");
        Text text = messages.getFirst();
        assertEquals("[LuaHUD] Hazır!", text.getString());
        assertEquals(0x12AB34, text.getStyle().getColor().getRgb());
        assertTrue(text.getStyle().isBold());
        assertFalse(text.getSiblings().getFirst().getStyle().isBold());
        assertTrue(text.getSiblings().getFirst().getStyle().isItalic());
        assertEquals(0xFFAA00, text.getSiblings().get(1).getStyle().getColor().getRgb());
    }

    @Test void modernEventsWorkInsideNestedTextOnEveryTarget() throws Exception {
        runtime().execute("""
                lh.sendMessage({text='', extra={{text='Kopyala',
                    click_event={action='copy_to_clipboard', value='örnek'},
                    hover_event={action='show_text', value={text='Açıklama', extra={{text='!'}}}}
                }}})
                """, "interactive-chat");
        Text text = messages.getFirst().getSiblings().getFirst();
        assertNotNull(text.getStyle().getClickEvent());
        assertNotNull(text.getStyle().getHoverEvent());
        //? if <1.21.5 {
        assertEquals("örnek", text.getStyle().getClickEvent().getValue());
        assertEquals("Açıklama!", text.getStyle().getHoverEvent()
                .getValue(net.minecraft.text.HoverEvent.Action.SHOW_TEXT).getString());
        //?} else {
        /*assertEquals("örnek", ((net.minecraft.text.ClickEvent.CopyToClipboard) text.getStyle().getClickEvent()).value());
        assertEquals("Açıklama!", ((net.minecraft.text.HoverEvent.ShowText) text.getStyle().getHoverEvent()).value().getString());
        *///?}
    }

    @Test void translationArgumentsRetainNumericAndBooleanTypes() throws Exception {
        runtime().execute("lh.sendMessage({translate='luahud.test.missing', fallback='%s / %s', with={42, true}})", "translated-chat");
        assertEquals("42 / true", messages.getFirst().getString());
    }

    @Test void invalidMessagesFailWithoutAddingChatEntries() throws Exception {
        CobaltRuntime runtime = runtime();
        for (String value : List.of("nil", "42", "true", "{}", "{color='red'}",
                "{text='x', extra={[2]={text='gap'}}}",
                "{text='x', clickEvent={action='copy_to_clipboard', value='x'}}")) {
            assertThrows(LuaError.class, () -> runtime.execute("lh.sendMessage(" + value + ")", "invalid-chat"), value);
        }
        assertTrue(messages.isEmpty());
    }

    @Test void stubsExposeTheMessageTypeAndLocalOnlyBehavior() {
        String stub = StubGenerator.generate(registry());
        assertTrue(stub.contains("---@class ChatText"));
        assertTrue(stub.contains("---@field text? string Literal text"));
        assertTrue(stub.contains("---@field extra? (string | ChatText)[]"));
        assertTrue(stub.contains("---@field with? (string | ChatText | number | boolean)[]"));
        assertTrue(stub.contains("---@field click_event? ChatClickEvent"));
        assertTrue(stub.contains("---@field hover_event? ChatHoverEvent"));
        assertTrue(stub.contains("---@class ChatOpenUrl"));
        assertTrue(stub.contains("---@field url string HTTP or HTTPS URL"));
        assertTrue(stub.contains("---@class ChatShowText"));
        assertTrue(stub.contains("---@field value string | ChatText Tooltip text component"));
        assertTrue(stub.contains("---@param message string | ChatText"));
        assertTrue(stub.contains("function lh.sendMessage(message) end"));
        assertTrue(stub.contains("never sends it to the server"));
    }

    @Test void documentationExportsRecursiveTextAndEventFields() {
        var types = JsonParser.parseString(DocumentationGenerator.generate(registry()))
                .getAsJsonObject().getAsJsonArray("types");
        var text = java.util.stream.StreamSupport.stream(types.spliterator(), false)
                .map(value -> value.getAsJsonObject())
                .filter(value -> value.get("name").getAsString().equals("ChatText"))
                .findFirst().orElseThrow();
        assertEquals("input_table", text.get("kind").getAsString());
        var extra = java.util.stream.StreamSupport.stream(text.getAsJsonArray("fields").spliterator(), false)
                .map(value -> value.getAsJsonObject())
                .filter(value -> value.get("name").getAsString().equals("extra"))
                .findFirst().orElseThrow();
        assertEquals("(string | ChatText)[]", extra.getAsJsonObject("type").get("display").getAsString());
        assertTrue(extra.get("optional").getAsBoolean());
    }

    @Test void schemasRejectInvalidNestedFieldsAndEventPayloads() throws Exception {
        CobaltRuntime runtime = runtime();
        for (String value : List.of(
                "{text='x', colour='red'}",
                "{text='x', extra={{text='y', colour='red'}}}",
                "{text='x', click_event={action='open_url', value='https://example.com'}}",
                "{text='x', hover_event={action='show_text', value={text='y', typo=true}}}",
                "{text='x', hover_event={action='show_entity', id='minecraft:pig'}}")) {
            assertThrows(LuaError.class, () -> runtime.execute("lh.sendMessage(" + value + ")", "invalid-schema"), value);
        }
        assertTrue(messages.isEmpty());
    }

    @Test void serverDependentContentAndActionsAreAbsentFromGeneratedTypes() {
        var fields = dev.ifair.luahud.api.models.ChatTypes.text().inputFields();
        String stub = StubGenerator.generate(registry());
        String docs = DocumentationGenerator.generate(registry());
        for (String name : List.of("score", "selector", "separator", "nbt", "interpret", "block", "entity", "storage")) {
            assertFalse(fields.containsKey(name), name);
        }
        for (String name : List.of("ChatScore", "ChatRunCommand", "ChatCustomClick", "ChatShowDialog")) {
            assertFalse(stub.contains(name), name);
            assertFalse(docs.contains(name), name);
        }
        assertTrue(stub.contains("ChatSuggestCommand"));
        assertTrue(stub.contains("ChatShowItem"));
        assertTrue(stub.contains("ChatShowEntity"));
    }

    @Test void serverDependentContentAndActionsAreRejectedRecursively() throws Exception {
        CobaltRuntime runtime = runtime();
        for (String value : List.of(
                "{score={name='player', objective='test'}}",
                "{selector='@a'}",
                "{nbt='name', storage='minecraft:test'}",
                "{text='x', separator=' / '}",
                "{type='score', text='x'}",
                "{type='selector', text='x'}",
                "{type='nbt', text='x'}",
                "{text='x', click_event={action='run_command', command='/say test'}}",
                "{text='x', click_event={action='custom', id='minecraft:test'}}",
                "{text='x', click_event={action='show_dialog', dialog='minecraft:test'}}",
                "{text='x', extra={{selector='@a'}}}",
                "{text='x', hover_event={action='show_text', value={selector='@a'}}}")) {
            assertThrows(LuaError.class, () -> runtime.execute("lh.sendMessage(" + value + ")", "server-content"), value);
        }
        assertTrue(messages.isEmpty());
    }

    @Test void suggestingACommandOnlyCreatesALocalClickAction() throws Exception {
        runtime().execute("lh.sendMessage({text='Öner', click_event={action='suggest_command', command='/help'}})", "local-suggestion");
        assertEquals(1, messages.size());
        assertNotNull(messages.getFirst().getStyle().getClickEvent());
    }
}
