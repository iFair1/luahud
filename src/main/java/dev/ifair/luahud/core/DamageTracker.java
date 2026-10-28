package dev.ifair.luahud.core;

import net.minecraft.entity.Entity;

/** Pairs nearby damage notifications and observed health/absorption loss on the client thread. */
public final class DamageTracker {
    private static final int MATCH_TICKS = 2;
    private Object player;
    private boolean healthInitialized;
    private float previousHealth;
    private DamageEvent pending;
    private double healthLost;
    private double absorptionLost;
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

    public void onAbsorptionUpdate(float previous, float current) {
        absorptionLost += Math.max(0.0, (double) previous - current);
    }

    /**
     * Nearby changes are combined with the latest damage source. Wait briefly when one
     * packet is missing; health loss without a notification uses minecraft:generic.
     * Absorption loss needs a notification so effect expiry alone is not reported as damage.
     */
    public DamageEvent tick() {
        if (pending == null && healthLost == 0.0 && absorptionLost == 0.0) return null;
        // Health and absorption arrive in separate packets, possibly on adjacent ticks.
        if (++waitingTicks < MATCH_TICKS) return null;
        DamageEvent event = pending == null
                ? (healthLost == 0.0 ? null : new DamageEvent("minecraft:generic", null, null, healthLost, player instanceof Entity entity ? entity : null))
                : pending.withAmount(healthLost + absorptionLost);
        clearPending();
        return event;
    }

    public boolean isIdle() {
        return pending == null && healthLost == 0.0 && absorptionLost == 0.0;
    }

    private void clearPending() {
        pending = null;
        healthLost = 0.0;
        absorptionLost = 0.0;
        waitingTicks = 0;
    }
}
