package org.gamboni.tech.history.event;

import com.fasterxml.jackson.annotation.JsonTypeInfo;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.SIMPLE_NAME;

@JsonTypeInfo(use = SIMPLE_NAME)
public interface Event {
}
