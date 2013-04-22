/*
 * Copyright (c) 2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.di.pipeline;

import org.labkey.api.admin.LoggerGetter;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.writer.ContainerUser;
import org.labkey.api.di.ScheduledPipelineJobContext;
import org.labkey.api.di.ScheduledPipelineJobDescriptor;
import org.apache.log4j.Logger;

import java.io.Serializable;

/**
 * User: jeckels
 * Date: 3/13/13
 */
public class TransformJobContext extends ScheduledPipelineJobContext implements ContainerUser, Serializable
{
    // add default ctor for jdk serialization so that
    // the pipeline can restart jobs in the jobstore.
    public TransformJobContext()
    {
    }

    public TransformJobContext(ScheduledPipelineJobDescriptor descriptor, Container container, User user)
    {
        super(descriptor, container, user);
    }
}
