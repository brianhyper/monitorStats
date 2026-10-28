package com.hyperbrains.platform.service.kuma;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Polls Kuma and keeps the latest parsed snapshot IN MEMORY ONLY (Kuma already stores history).
 * Failure model: Kuma unreachable or snapshot stale -> UNKNOWN, never DOWN.
 */
@Service
@ConditionalOnProperty(name = "hbs.kuma.mode", havingValue = "embedded", matchIfMissing = true)
public class KumaStatusService implements KumaSource {

    private static final Logger LOG = LoggerFactory.getLogger(KumaStatusService.class);

    private record Snapshot(Map<String, KumaMonitorStatus> monitors, Instant fetchedAt) {}

    private final KumaClient client;
    private final boolean enabled;
    private final Duration staleAfter;
    private volatile Snapshot snapshot;
    private volatile boolean lastRefreshFailed;

    public KumaStatusService(
        KumaClient client,
        @Value("${hbs.kuma.enabled:false}") boolean enabled,
        @Value("${hbs.kuma.stale-after-seconds:180}") long staleAfterSeconds
    ) {
        this.client = client;
        this.enabled = enabled;
        this.staleAfter = Duration.ofSeconds(staleAfterSeconds);
    }

    @Scheduled(initialDelay = 5_000, fixedDelayString = "${hbs.kuma.poll-interval-ms:60000}")
    public void refresh() {
        if (!enabled) {
            return;
        }
        try {
            Map<String, KumaMonitorStatus> parsed = KumaMetricsParser.parse(client.fetchMetrics());
            snapshot = new Snapshot(parsed, Instant.now());
            lastRefreshFailed = false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            // Log once per outage, not every minute. Keep the last snapshot; staleness check handles it.
            if (!lastRefreshFailed) {
                LOG.warn("Kuma poll failed: {}", e.toString());
            }
            lastRefreshFailed = true;
        }
    }

    /** Everything Kuma currently reports. Stale or disabled -> unreachable (UNKNOWN), never DOWN. */
    @Override
    public KumaSnapshot current() {
        Snapshot s = snapshot;
        if (!enabled || s == null || Instant.now().isAfter(s.fetchedAt().plus(staleAfter))) {
            return new KumaSnapshot(false, s == null ? null : s.fetchedAt(), Map.of());
        }
        return new KumaSnapshot(true, s.fetchedAt(), s.monitors());
    }
}