package org.gamboni.tech.history.event;

import org.gamboni.tech.web.js.JS;

@JS
public record ElementRemovedEvent(String key, String id) implements Event {
}
