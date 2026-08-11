package me.ifair.luahud.api;

import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.TwoArgFunction;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

public class LazyTable {
    private final Map<String, Supplier<LuaValue>> vars = new HashMap<>();

    // Separate methods for primitive types
    public void addDouble(String name, DoubleSupplier supplier) {
        vars.put(name, () -> LuaValue.valueOf(supplier.getAsDouble()));
    }

    public void addInt(String name, IntSupplier supplier) {
        vars.put(name, () -> LuaValue.valueOf(supplier.getAsInt()));
    }

    public void addBoolean(String name, BooleanSupplier supplier) {
        vars.put(name, () -> LuaValue.valueOf(supplier.getAsBoolean()));
    }

    // String is slightly different
    public void addString(String name, Supplier<String> supplier) {
        vars.put(name, () -> {
            String val = supplier.get();
            return val == null ? LuaValue.NIL : LuaValue.valueOf(val);
        });
    }

    // This is for nested static tables, not dynamic fancy things
    public void addTable(String name, LuaTable table) {
        vars.put(name, () -> table);
    }

    public void addLazyTable(String name, Supplier<LuaTable> tableSupplier) {
        vars.put(name, () -> {
            LuaTable table = tableSupplier.get();
            return table == null ? LuaValue.NIL : table;
        });
    }

    public void add(String name, Supplier<LuaValue> supplier) {
        vars.put(name, supplier);
    }

    public LuaTable build() {
        LuaTable table = new LuaTable();
        LuaTable metatable = new LuaTable();

        metatable.set(LuaValue.INDEX, new TwoArgFunction() {
            @Override
            public LuaValue call(LuaValue table, LuaValue key) {
                if (!key.isstring()) return LuaValue.NIL;

                String varName = key.checkjstring();
                Supplier<LuaValue> provider = vars.get(varName);

                return provider != null ? provider.get() : LuaValue.NIL;
            }
        });

        table.setmetatable(metatable);
        return table;
    }
}
