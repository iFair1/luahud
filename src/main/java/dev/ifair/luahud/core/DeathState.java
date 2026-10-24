package dev.ifair.luahud.core;

/** Connection-scoped death history, retained across respawns and dimension changes. */
public interface DeathState {
    DeathEvent luahud$getLastDeath();
}
