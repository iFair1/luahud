package me.ifair.luahud.api;

import me.ifair.luahud.render.RenderBuffer;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.TwoArgFunction;

public class RenderModule implements ApiModule{
   @Override
    public void register(LuaTable lh) {
       lh.set("drawText", new TwoArgFunction() {
           @Override
           public LuaValue call(LuaValue text, LuaValue options) {
               String str = text.checkjstring();
               // not drawing here. This is only for updating the buffer

               RenderBuffer.addText(str, options.opttable(null));
               return LuaValue.NIL;
           }
       });

       lh.set("section", new TwoArgFunction() {
           @Override
           public LuaValue call(LuaValue options, LuaValue callback) {
                 RenderBuffer.pushSection(options.checktable());
                 if (callback.isfunction()) {
                     callback.call(); // run the section
                 }
                 RenderBuffer.popSection();
                 return LuaValue.NIL;
           }
       });
   }
}
