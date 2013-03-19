package org.labkey.di.pipeline;

import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.di.api.ScheduledPipelineJobContext;
import org.labkey.di.api.ScheduledPipelineJobDescriptor;

import java.io.Serializable;

/**
 * User: jeckels
 * Date: 3/13/13
 */
public class TransformJobContext extends ScheduledPipelineJobContext implements Serializable
{
    public TransformJobContext(ScheduledPipelineJobDescriptor descriptor, Container container, User user)
    {
        super(descriptor, container, user);
    }
}
