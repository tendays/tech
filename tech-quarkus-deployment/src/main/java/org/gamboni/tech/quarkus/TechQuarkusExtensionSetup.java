package org.gamboni.tech.quarkus;

import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import org.gamboni.tech.history.event.ElementRemovedEvent;
import org.gamboni.tech.history.event.NewStateEvent;
import org.gamboni.tech.history.event.StampedEventList;
import org.gamboni.tech.history.event.TextEvent;

public class TechQuarkusExtensionSetup {
    @BuildStep
    public ReflectiveClassBuildItem reflectionSetup() {
        return ReflectiveClassBuildItem.builder(
                        ElementRemovedEvent.class,
                        NewStateEvent.class,
                        TextEvent.class,
                        StampedEventList.class
                ).constructors()
                .fields()
                .methods()
                .classes()
                .build();
    }
}
