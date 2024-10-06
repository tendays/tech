package org.gamboni.tech.quarkus;

import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import org.gamboni.tech.history.StampedEventList;
import org.gamboni.tech.history.ui.NewStateEvent;
import org.gamboni.tech.history.ui.TextEvent;

public class TechQuarkusExtensionSetup {
    @BuildStep
    public ReflectiveClassBuildItem reflectionSetup() {
        return ReflectiveClassBuildItem.builder(
                TextEvent.class,
                NewStateEvent.class,
                StampedEventList.class
        ).constructors()
                .fields()
                .methods()
                .classes()
                .build();
    }
}
