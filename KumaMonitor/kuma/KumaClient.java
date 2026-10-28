package com.hyperbrains.platform.service.kuma;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Fetches Kuma's /metrics over the internal Docker network. Uses the JDK HttpClient: no new dependency. */
@Component
@ConditionalOnProperty(name = "hbs.kuma.mode", havingValue = "embedded", matchIfMissing = true)
public class KumaClient {

    private final HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(3)).build();
    private final String baseUrl;
    private final String authHeader;

    public KumaClient(
        @Value("${hbs.kuma.base-url:http://uptime-kuma:3001}") String baseUrl,
        @Value("${hbs.kuma.api-key:}") String apiKey
    ) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        // Kuma API keys use basic auth with an empty username.
        this.authHeader = "Basic " + Base64.getEncoder().encodeToString((":" + apiKey).getBytes(StandardCharsets.UTF_8));
    }

    /** @throws IOException on non-200 or network failure. The API key is never put in the message. */
    public String fetchMetrics() throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/metrics"))
            .timeout(Duration.ofSeconds(5))
            .header("Authorization", authHeader)
            .GET()
            .build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) {
            throw new IOException("Kuma /metrics returned HTTP " + res.statusCode());
        }
        return res.body();
    }
}