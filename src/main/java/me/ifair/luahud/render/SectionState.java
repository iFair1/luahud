package me.ifair.luahud.render;

public class SectionState {
    // Default values
    public Anchor anchor = Anchor.TOP_LEFT;
    public int offsetX = 2;
    public int offsetY = 2;
    public boolean shadow = true;
    public int textColor = 0xFFFFFFFF;
    public int backgroundColor = 0x80000000;
    public float scale = 1.0f;

    public SectionState copy() {
        SectionState state = new SectionState();
        state.anchor = this.anchor;
        state.offsetX = this.offsetX;
        state.offsetY = this.offsetY;
        state.shadow = this.shadow;
        state.textColor = this.textColor;
        state.backgroundColor = this.backgroundColor;
        state.scale = this.scale;
        return state;
    }
}