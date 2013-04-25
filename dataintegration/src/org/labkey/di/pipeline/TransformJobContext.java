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

import org.labkey.api.data.Container;
import org.labkey.api.data.JdbcType;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.security.User;
import org.labkey.api.writer.ContainerUser;
import org.labkey.api.di.ScheduledPipelineJobContext;
import org.labkey.api.di.ScheduledPipelineJobDescriptor;
import org.labkey.di.VariableDescription;
import org.labkey.di.VariableMapImpl;

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


    /* VARIABLES */

    VariableMapImpl jobVariables;


    // these are known built-in variables, might become property descriptors or protocolparameters or something,
    // just going to use an enum for now
    public enum Variable implements VariableDescription
    {
        IncrementalStartTimestamp(JdbcType.TIMESTAMP),
        IncrementalEndTimestamp(JdbcType.TIMESTAMP),
        TranformRunId(JdbcType.INTEGER),
        UserId(JdbcType.INTEGER),
        RecordsInserted(JdbcType.BIGINT),
        RecordsDeleted(JdbcType.BIGINT)
        ;

        final JdbcType _type;

        Variable(JdbcType type)
        {
            _type = type;
        }


        @Override
        public String getName()
        {
            return this.name();
        }

        @Override
        public String getURI()
        {
            return getClass().getName() + "#" + getName();
        }

        @Override
        public JdbcType getType()
        {
            return _type;
        }

        @Override
        public RecordedAction.ParameterType getParameterType()
        {
            PropertyType pt = PropertyType.INTEGER;
            if (getType().isDateOrTime())
                pt = PropertyType.DATE_TIME;
            return new RecordedAction.ParameterType(getName(), getURI(), pt);
        }
    }
}
