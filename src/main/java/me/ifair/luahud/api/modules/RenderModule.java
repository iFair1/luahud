package me.ifair.luahud.api.modules;

import me.ifair.luahud.api.core.ApiModule;
import me.ifair.luahud.render.RenderBuffer;
import net.minecraft.item.ItemStack;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.TwoArgFunction;

public class RenderModule implements ApiModule {
    @Override
    public void register(LuaTable lh) {

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
                RenderBuffer.addText(str, options.opttable(null), true);
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

        lh.set("section", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue options) {
                if (options.istable()) {
                    RenderBuffer.setSection(options.checktable());
                }
                return LuaValue.NIL;
            }
        });
    }
}