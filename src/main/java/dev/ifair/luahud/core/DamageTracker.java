package dev.ifair.luahud.core;

/** Pairs nearby damage notifications and observed health loss on the client thread. */
public final class DamageTracker {
    private static final int MATCH_TICKS = 2;
    private Object player;
    private boolean healthInitialized;
    private float previousHealth;
    private DamageEvent pending;
    private double healthLost;
    private int waitingTicks;

    /** A replacement player (including respawn) starts a fresh health baseline. */
    public void bind(Object player) {
        if (this.player == player) return;
        this.player = player;
        healthInitialized = false;
        clearPending();
    }

    public void onDamage(DamageEvent notification) {
        pending = notification;
    }

    public void onHealthUpdate(float current) {
        // The first packet initializes health; it is not damage from the default spawn health.
        if (!healthInitialized) {
            healthInitialized = true;
            previousHealth = current;
            return;
        }
        // Keep our own packet baseline: entity metadata may already have changed getHealth().
        healthLost += Math.max(0.0, (double) previousHealth - current);
        previousHealth = current;
    }

    /**
     * Nearby changes are combined with the latest damage source. Wait briefly when one
     * packet is missing; health loss without a notification uses minecraft:generic.
     */
    public DamageEvent tick() {
        if (pending == null && healthLost == 0.0) return null;
        if ((pending == null || healthLost == 0.0) && ++waitingTicks < MATCH_TICKS) return null;
        DamageEvent event = pending == null
                ? new DamageEvent("minecraft:generic", null, null, healthLost)
                : pending.withAmount(healthLost);
        clearPending();
        return event;
    }

    private void clearPending() {
        pending = null;
        healthLost = 0.0;
        waitingTicks = 0;
    }
}
