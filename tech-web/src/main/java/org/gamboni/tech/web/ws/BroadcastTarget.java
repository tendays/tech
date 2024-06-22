package org.gamboni.tech.web.ws;

public interface BroadcastTarget {
    void sendOrThrow(Object payload);

    void sendOrLog(Object payload);

    void onClose(Runnable task);

    boolean isOpen();

    void markClosed();
}
