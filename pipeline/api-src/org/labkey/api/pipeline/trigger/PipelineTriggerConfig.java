package org.labkey.api.pipeline.trigger;

import org.labkey.api.data.Container;

import java.nio.file.Path;
import java.util.Date;

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

    String getContainerId();

    Container lookupContainer();

    int getCreatedBy();

    void setCreatedBy(int createdBy);

    Date getCreated();

    void setCreated(Date created);

    int getModifiedBy();

    void setModifiedBy(int modifiedBy);

    Date getModified();

    void setModified(Date modified);

    PipelineTriggerType getPipelineTriggerType();

    void start();

    void stop();

    boolean matches(Path directory, Path entry);
}