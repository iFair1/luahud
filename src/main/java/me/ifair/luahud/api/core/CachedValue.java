package me.ifair.luahud.api.core;

import java.util.function.Supplier;

public class CachedValue<T> implements Supplier<T> {
    private final Supplier<T> actualSupplier;
    private final long cooldownMs;
    private long lastUpdate = 0;
    private T cachedData;

    public CachedValue(long cooldownMs, Supplier<T> actualSupplier) {
        this.cooldownMs = cooldownMs;
        this.actualSupplier = actualSupplier;
    }

    @Override
    public T get() {
        long now = System.currentTimeMillis();

        if (cachedData == null || now - lastUpdate >= cooldownMs) {
            cachedData = actualSupplier.get();
            lastUpdate = now;
        }
        return cachedData;
    }
}
