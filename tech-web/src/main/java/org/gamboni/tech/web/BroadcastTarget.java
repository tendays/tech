package org.gamboni.tech.web;

public interface BroadcastTarget {
    void sendOrThrow(Object payload);

    void sendOrLog(Object payload);

    void onClose(Runnable task);
}
