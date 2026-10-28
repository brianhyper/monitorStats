package com.hyperbrains.platform.service.kuma;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Kuma at one moment. Same JSON shape whether it comes from the embedded poller or the remote
 * monitor container, which is what makes "subsystem first, merge later" a config switch.
 *
 * @param reachable true only when the data is fresh; false means "we don't know", never "down"
 */
public record KumaSnapshot(boolean reachable, Instant fetchedAt, Map<String, KumaMonitorStatus> monitors) {
    public KumaSnapshot {
        monitors = monitors == null ? Map.of() : monitors;
    }

    public static KumaSnapshot unreachable() {
        return new KumaSnapshot(false, null, Map.of());
    }

    /**
     * Status for specific monitor ids (the future Application entity's kumaMonitorId).
     * Not reachable -> UNKNOWN. Reachable but id absent -> NO_DATA (paused or removed in Kuma).
     */
    public Map<String, KumaMonitorStatus> statusFor(Collection<String> monitorIds) {
        Map<String, KumaMonitorStatus> out = new LinkedHashMap<>();
        for (String id : monitorIds) {
            KumaMonitorStatus found = monitors.get(id);
            out.put(
                id,
                found != null ? found : KumaMonitorStatus.placeholder(id, reachable ? KumaStatus.NO_DATA : KumaStatus.UNKNOWN)
            );
        }
        return out;
    }
}