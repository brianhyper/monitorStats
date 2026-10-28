package com.hyperbrains.platform.service.kuma;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class KumaMetricsParserTest {

    private static final String BASE = "monitor_id=\"%s\",monitor_name=\"%s\",monitor_type=\"http\",monitor_url=\"%s\",monitor_hostname=\"null\",monitor_port=\"null\"";

    private static String line(String metric, String id, String name, String url, String value, String extra) {
        return metric + "{" + String.format(BASE, id, name, url) + extra + "} " + value;
    }

    @Test
    void upMonitorWithoutCertHasNullCertFields() {
        var p = KumaMetricsParser.parse(
            String.join(
                "\n",
                line("monitor_status", "1", "caddy", "http://x", "1", ""),
                line("monitor_response_time", "1", "caddy", "http://x", "5", ""),
                line("monitor_response_time_seconds", "1", "caddy", "http://x", "0.0048", ",window=\"1d\"")
            )
        );
        var m = p.get("1");
        assertThat(m.status()).isEqualTo(KumaStatus.UP);
        assertThat(m.latencyMs()).isEqualTo(5.0);
        assertThat(m.avg24hMs()).isEqualTo(4.8);
        assertThat(m.certDaysRemaining()).isNull();
        assertThat(m.certValid()).isNull();
    }

    @Test
    void downMonitorSentinelsBecomeNull() {
        var p = KumaMetricsParser.parse(
            String.join(
                "\n",
                line("monitor_status", "9", "Test down", "http://x", "0", ""),
                line("monitor_response_time", "9", "Test down", "http://x", "-1", ""),
                line("monitor_response_time_seconds", "9", "Test down", "http://x", "0", ",window=\"1d\"")
            )
        );
        var m = p.get("9");
        assertThat(m.status()).isEqualTo(KumaStatus.DOWN);
        assertThat(m.latencyMs()).isNull();
        assertThat(m.avg24hMs()).isNull();
    }

    @Test
    void unobservedStatusCodeIsUnknownAndNamesAreTrimmed() {
        var p = KumaMetricsParser.parse(
            String.join("\n", line("monitor_status", "5", " vault", "http://x", "2", ""), "nodejs_heap_size_total_bytes 1")
        );
        assertThat(p.get("5").status()).isEqualTo(KumaStatus.UNKNOWN);
        assertThat(p.get("5").name()).isEqualTo("vault");
        assertThat(p).hasSize(1);
    }

    @Test
    void garbageInputDoesNotThrow() {
        assertThat(KumaMetricsParser.parse(null)).isEmpty();
        assertThat(KumaMetricsParser.parse("<html>502</html>")).isEmpty();
    }
}