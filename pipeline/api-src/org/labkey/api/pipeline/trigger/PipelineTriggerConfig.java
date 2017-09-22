package org.labkey.api.pipeline.trigger;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;

import java.nio.file.Path;
import java.util.Date;
import java.util.Map;

/**
 * Pipeline Trigger configuration.
 */
public interface PipelineTriggerConfig
{
    int getRowId();

    String getName();

    String getDescription();

    String getType();

    Date getLastChecked();

    boolean isEnabled();

    String getConfiguration();

    String getPipelineId();

    Container lookupContainer();

    PipelineTriggerType getPipelineTriggerType();

    void start();

    void stop();

    boolean matches(Path directory, Path entry, /*out*/ @Nullable Map<String, String> namedGroupSubstitutions);

    String getStatus();
}