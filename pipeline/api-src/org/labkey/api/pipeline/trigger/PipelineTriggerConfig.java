package org.labkey.api.pipeline.trigger;

import org.labkey.api.data.Container;
import org.labkey.api.security.User;

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

    PipelineTriggerType getPipelineTriggerType();

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
}