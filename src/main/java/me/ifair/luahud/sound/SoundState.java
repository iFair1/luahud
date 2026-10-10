package me.ifair.luahud.sound;

/** World-scoped jukebox state exposed by the sound tracking mixins. */
public interface SoundState {
    RecordTracker.PlayingRecord luahud$getRecord();

    void luahud$setSoundPaused(boolean paused);
}
