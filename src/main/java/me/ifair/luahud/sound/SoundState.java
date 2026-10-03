package me.ifair.luahud.sound;

/** Sound state supplied by the world renderer mixin. */
public interface SoundState {
    RecordTracker.PlayingRecord luahud$getRecord();
}
