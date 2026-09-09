package me.ifair.luahud.api;

public final class LuaParameter<T> {
    private final Object owner;
    private final int index;
    private final String name;
    private final LuaType<T> type;
    private final LuaDoc doc;

    public LuaParameter(Object owner, int index, String name, LuaType<T> type, LuaDoc doc) {
        this.owner = owner;
        this.index = index;
        this.name = name;
        this.type = type;
        this.doc = doc;
    }

    public String name() { return name; }
    public LuaType<T> type() { return type; }
    public LuaDoc doc() { return doc; }
    public int index() { return index; }
    public Object owner() { return owner; }
}
