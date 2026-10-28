package com.hyperbrains.platform.service.kuma;

/**
 * Snapshot of one Kuma monitor. Nullable fields mean "no data", never zero.
 *
 * @param latencyMs last check latency in ms; null when down or no response (Kuma sends -1)
 * @param avg24hMs  24h average response in ms; null unless UP (Kuma sends 0 when down)
 */
public record KumaMonitorStatus(
    String monitorId,
    String name,
    String type,
    String url,
    KumaStatus status,
    Double rawStatus,
    Double latencyMs,
    Double avg24hMs,
    Double uptime24h,
    Double uptime30d,
    Integer certDaysRemaining,
    Boolean certValid
) {
    public static KumaMonitorStatus placeholder(String monitorId, KumaStatus status) {
        return new KumaMonitorStatus(monitorId, null, null, null, status, null, null, null, null, null, null, null);
    }
}