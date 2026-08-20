package me.ifair.luahud.render;

public class SectionState {
    // Default values
    public String anchor = "topLeft";
    public int offsetX = 2;
    public int offsetY = 2;
    public boolean shadow = true;
    public int textColor = 0xFFFFFFFF;
    public int backgroundColor = 0x80000000;

    public SectionState copy() {
        SectionState state = new SectionState();
        state.anchor = this.anchor;
        state.offsetX = this.offsetX;
        state.offsetY = this.offsetY;
        state.textColor = this.textColor;
        state.backgroundColor = this.backgroundColor;
        return state;
    }
}