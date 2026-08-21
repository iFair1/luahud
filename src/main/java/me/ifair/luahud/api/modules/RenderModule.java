package me.ifair.luahud.api.modules;

import me.ifair.luahud.api.core.ApiModule;
import me.ifair.luahud.render.RenderBuffer;
import me.ifair.luahud.render.SectionState;
import net.minecraft.item.ItemStack;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.TwoArgFunction;

public class RenderModule implements ApiModule {

    private static SectionState currentState = new SectionState();

    @Override
    public void register(LuaTable lh) {

        lh.set("section", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue options) {
                currentState = new SectionState();

                if (options.istable()) {
                    LuaTable table = options.checktable();
                    if (!table.get("anchor").isnil()) currentState.anchor = table.get("anchor").checkjstring();

                    if (!table.get("textColor").isnil()) currentState.textColor = parseColor(table.get("textColor").checkjstring());
                    if (!table.get("backgroundColor").isnil()) currentState.backgroundColor = parseColor(table.get("backgroundColor").checkjstring());

                    if (!table.get("offsetX").isnil()) currentState.offsetY = table.get("offsetX").checkint();
                    if (!table.get("offsetY").isnil()) currentState.offsetY = table.get("offsetY").checkint();

                    if (!table.get("shadow").isnil()) currentState.shadow = table.get("shadow").checkboolean();
                    if (!table.get("scale").isnil()) currentState.scale = (float) table.get("scale").checkdouble(); // scale is not defined in currentState yet
                }

                return LuaValue.NIL;
            }
        });

        lh.set("draw", new TwoArgFunction() {
            @Override
            public LuaValue call(LuaValue text, LuaValue options) {
                String str = text.tojstring();
                RenderBuffer.addText(str, options.opttable(null), false);
                return LuaValue.NIL;
            }
        });

        lh.set("drawln", new TwoArgFunction() {
            @Override
            public LuaValue call(LuaValue text, LuaValue options) {
                String str = text.tojstring();
                addText(text.tojstring(), options.opttable(null), true);
                return LuaValue.NIL;
            }
        });

        lh.set("drawStack", new TwoArgFunction() {
            @Override
            public LuaValue call(LuaValue stack, LuaValue options) {
                // Create an ItemStack object with stack
                // Send it to RenderBuffer as .addStack(ItemStack itemStack, options.opttable(null))
            }
        });


    }

    private void addText(String text, LuaTable options, boolean newLine) {
        int textColor = resolveColor(options, "textColor", currentState.textColor);
        int backgroundColor = resolveColor(options, "backgroundColor", currentState.backgroundColor);
        boolean shadow = resolveBoolean(options, "shadow", currentState.shadow);

        // I'll update RenderBuffer
        RenderBuffer.addText(text, textColor, backgroundColor, shadow, currentState.anchor, currentState.offsetX, currentState.offsetY, newLine);
    }

    private static int resolveColor(LuaTable options, String key, int fallback) {
        if (options != null && !options.get(key).isnil()) {
            return parseColor(options.get(key).checkjstring());
        }
        return fallback;
    }

    private static int parseColor(String hex) {
        if (hex.startsWith("#")) hex = hex.substring(1);
        if (hex.length() == 6) hex = "FF" + hex;
        return (int) Long.parseLong(hex, 16);
    }

    private static boolean resolveBoolean(LuaTable options, String key, boolean fallback) {
        if (options != null && !options.get(key).isnil()) {
            return options.get(key).checkboolean();
        }
        return fallback;
    }
}