package org.gamboni.tech.history.event;

import org.gamboni.tech.web.js.JS;

@JS
public record TextEvent(String key, String id, String text) implements Event {
}
