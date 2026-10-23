/* Copyright 2026. GPL-3.0-only. */
package ru.playsoftware.j2meloader.runtime;

/** GL-thread policy for optional intermediate work, never for guest frame delivery. */
public final class InterpolationLoadGuard {
    private static final long COOLDOWN_NANOS = 500_000_000L;
    private boolean cooling;
    private long resumeAfter;
    private int pressure, healthy;

    public static long refreshPeriod(float hz) {
        return (long) (1_000_000_000d / (hz >= 30 && hz <= 240 ? hz : 60));
    }

    public void reset() { cooling = false; pressure = healthy = 0; }

    public boolean allow(long now, boolean gpuBehind) {
        if (gpuBehind) suppress(now);
        if (cooling && now - resumeAfter >= 0 && healthy >= 3) reset();
        return !cooling;
    }

    public void submitted(long now, long cpuNanos, long budgetNanos) {
        if (cpuNanos >= budgetNanos) {
            healthy = 0;
            if (++pressure >= 3) suppress(now);
        } else if (cpuNanos < budgetNanos * 3 / 4) {
            healthy = Math.min(3, healthy + 1);
            pressure = Math.max(0, pressure - 1);
        } else {
            healthy = 0;
        }
    }

    private void suppress(long now) {
        cooling = true; resumeAfter = now + COOLDOWN_NANOS; pressure = healthy = 0;
    }
}
