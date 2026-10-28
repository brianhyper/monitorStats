package com.hyperbrains.platform.service.kuma;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses Uptime Kuma's Prometheus /metrics text (verified against Kuma 2.5.8).
 * Rules come from observed behavior:
 *  - monitor_status: 1 = UP, 0 = DOWN, anything else = UNKNOWN (2/3 not yet observed)
 *  - monitor_response_time (ms): -1 = no response
 *  - monitor_response_time_seconds{window}: 0 when down means "no data"
 *  - cert metrics are absent for plain HTTP or failed checks
 *  - paused/removed monitors disappear entirely (handled by the caller as NO_DATA)
 *  - match on monitor_id, never on name
 */
public final class KumaMetricsParser {

    private static final Pattern LINE = Pattern.compile("^(monitor_\\w+)\\{(.*)\\}\\s+(\\S+)\\s*$");
    private static final Pattern LABEL = Pattern.compile("(\\w+)=\"((?:[^\"\\\\]|\\\\.)*)\"");

    private KumaMetricsParser() {}

    private static final class Raw {
        String name;
        String type;
        String url;
        final Map<String, Double> metrics = new HashMap<>();
    }

    /** Returns monitorId -> status. Ignores comments, non-monitor metrics and malformed lines. */
    public static Map<String, KumaMonitorStatus> parse(String text) {
        Map<String, Raw> raw = new LinkedHashMap<>();
        if (text == null) {
            return Map.of();
        }
        for (String line : text.split("\\R")) {
            if (line.startsWith("#")) {
                continue;
            }
            Matcher m = LINE.matcher(line);
            if (!m.matches()) {
                continue;
            }
            String metric = m.group(1);
            Map<String, String> labels = labels(m.group(2));
            String id = labels.get("monitor_id");
            if (id == null) {
                continue;
            }
            Raw r = raw.computeIfAbsent(id, k -> new Raw());
            r.name = labels.getOrDefault("monitor_name", "").strip();
            r.type = labels.get("monitor_type");
            r.url = labels.get("monitor_url");
            Double v = number(m.group(3));
            String window = labels.get("window");
            r.metrics.put(window == null ? metric : metric + "|" + window, v);
        }

        Map<String, KumaMonitorStatus> out = new LinkedHashMap<>();
        raw.forEach((id, r) -> out.put(id, build(id, r)));
        return out;
    }

    private static KumaMonitorStatus build(String id, Raw r) {
        Double s = r.metrics.get("monitor_status");
        KumaStatus status = s == null ? KumaStatus.UNKNOWN
            : s == 1.0 ? KumaStatus.UP
            : s == 0.0 ? KumaStatus.DOWN
            : KumaStatus.UNKNOWN;
        boolean up = status == KumaStatus.UP;

        Double lat = r.metrics.get("monitor_response_time");
        Double latencyMs = (!up || lat == null || lat < 0) ? null : lat;

        Double avg = r.metrics.get("monitor_response_time_seconds|1d");
        Double avg24hMs = (!up || avg == null) ? null : Math.round(avg * 10_000.0) / 10.0;

        Double days = r.metrics.get("monitor_cert_days_remaining");
        Double valid = r.metrics.get("monitor_cert_is_valid");

        return new KumaMonitorStatus(
            id, r.name, r.type, r.url, status, s, latencyMs, avg24hMs,
            r.metrics.get("monitor_uptime_ratio|1d"),
            r.metrics.get("monitor_uptime_ratio|30d"),
            days == null ? null : days.intValue(),
            valid == null ? null : valid == 1.0
        );
    }

    private static Map<String, String> labels(String s) {
        Map<String, String> out = new HashMap<>();
        Matcher m = LABEL.matcher(s);
        while (m.find()) {
            out.put(m.group(1), unescape(m.group(2)));
        }
        return out;
    }

    private static String unescape(String s) {
        if (s.indexOf('\\') < 0) {
            return s;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char n = s.charAt(++i);
                sb.append(n == 'n' ? '\n' : n);
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static Double number(String s) {
        try {
            double d = Double.parseDouble(s);
            return Double.isNaN(d) || Double.isInfinite(d) ? null : d;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}