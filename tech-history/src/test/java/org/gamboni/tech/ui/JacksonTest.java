package org.gamboni.tech.ui;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.gamboni.tech.history.event.ElementRemovedEvent;
import org.gamboni.tech.history.event.StampedEventList;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JacksonTest {

    @Test
    public void testObjectSerialisation() throws Exception {
        var object = new ElementRemovedEvent("the key", "the id");
        assertEquals("{\"@type\":\"ElementRemovedEvent\",\"key\":\"the key\",\"id\":\"the id\"}",
                new ObjectMapper().writeValueAsString(object));
    }

    @Test
    public void testEventListSerialisation() throws Exception {
        var object = new StampedEventList(1, List.of(new ElementRemovedEvent("the key", "the id")));
        assertEquals("{\"stamp\":1,\"updates\":[{\"@type\":\"ElementRemovedEvent\",\"key\":\"the key\",\"id\":\"the id\"}]}",
                new ObjectMapper().writeValueAsString(object));
    }
}
