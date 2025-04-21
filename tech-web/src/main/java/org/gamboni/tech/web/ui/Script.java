package org.gamboni.tech.web.ui;

import com.google.common.base.Preconditions;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

import static java.util.stream.Collectors.joining;
import static org.gamboni.tech.web.ui.Html.attribute;

/**
 * @author tendays
 */
public class Script implements Resource {

    private final List<ScriptMember> members = new ArrayList<>();

    private boolean exposed = false;

    @Getter
    @Setter
    private String url = "/script.js";

    public void add(ScriptMember member) {
        Preconditions.checkState(!exposed, "Adding elements to a script is not allowed once it has stared rendering!");
        this.members.add(member);
    }

    public boolean isEmpty() {
        return members.isEmpty();
    }

    @Override
    public String render() {
        this.exposed = true;
        return members
                .stream()
                .map(ScriptMember::render)
                .collect(joining());
    }

    @Override
    public Html asElement() {
        return new Element("script",
                List.of(
                        attribute("type", "text/javascript"),
                        attribute("src", getUrl())
                ));
    }

    @Override
    public String getMime() {
        return "text/javascript";
    }
}
