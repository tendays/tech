package org.gamboni.tech.history.ui;

import org.gamboni.tech.web.js.JS;

@JS
public record NewStateEvent<E extends Enum<?>>(String key, String id, E newState) {}

