package com.hyperbrains.platform.service.kuma;

/** Where HBS gets Kuma data from: the embedded poller, or a remote monitor container. */
public interface KumaSource {
    KumaSnapshot current();
}