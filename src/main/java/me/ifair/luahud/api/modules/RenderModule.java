package me.ifair.luahud.api.modules;

import me.ifair.luahud.api.core.ApiModule;
import me.ifair.luahud.render.RenderBuffer;
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
                RenderBuffer.addText(str, options.opttable(null));
                return LuaValue.NIL;
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