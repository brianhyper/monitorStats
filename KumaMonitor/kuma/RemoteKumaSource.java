package com.hyperbrains.platform.service.kuma;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Subsystem mode: asks the separate kuma-monitor container for its snapshot.
 * Any failure (container down, timeout, bad JSON) -> unreachable (UNKNOWN), never DOWN.
 */
@Component
@ConditionalOnProperty(name = "hbs.kuma.mode", havingValue = "remote")
public class RemoteKumaSource implements KumaSource {

    private static final Logger LOG = LoggerFactory.getLogger(RemoteKumaSource.class);
    private static final long CACHE_MS = 5_000;

    private final HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(2)).build();
    private final ObjectMapper mapper;
    private final URI statusUri;

    private volatile KumaSnapshot cached;
    private volatile long cachedAt;
    private volatile boolean lastFailed;

    public RemoteKumaSource(ObjectMapper mapper, @Value("${hbs.kuma.remote-url:http://kuma-monitor:8080}") String remoteUrl) {
        this.mapper = mapper;
        this.statusUri = URI.create(remoteUrl.replaceAll("/+$", "") + "/status");
    }

    @Override
    public KumaSnapshot current() {
        // Tiny cache so a page refreshing every few seconds doesn't hammer the monitor.
        KumaSnapshot c = cached;
        if (c != null && System.currentTimeMillis() - cachedAt < CACHE_MS) {
            return c;
        }
        KumaSnapshot fresh;
        try {
            HttpRequest req = HttpRequest.newBuilder(statusUri).timeout(Duration.ofSeconds(3)).GET().build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) {
                throw new IOException("kuma-monitor returned HTTP " + res.statusCode());
            }
            fresh = mapper.readValue(res.body(), KumaSnapshot.class);
            if (lastFailed) {
                LOG.info("kuma-monitor reachable again");
            }
            lastFailed = false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fresh = KumaSnapshot.unreachable();
        } catch (Exception e) {
            if (!lastFailed) {
                LOG.warn("kuma-monitor call failed: {}", e.toString());
            }
            lastFailed = true;
            fresh = KumaSnapshot.unreachable();
        }
        cached = fresh;
        cachedAt = System.currentTimeMillis();
        return fresh;
    }
}