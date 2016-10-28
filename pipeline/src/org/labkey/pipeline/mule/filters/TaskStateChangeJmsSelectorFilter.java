/*
 * Copyright (c) 2007-2016 LabKey Corporation
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
package org.labkey.pipeline.mule.filters;

import org.labkey.api.pipeline.PipelineJob;
import org.mule.providers.jms.filters.JmsSelectorFilter;

/**
 * <code>TaskStateChangeJmsSelectorFilter</code> builds and applies a JMS selector for
 * for all jobs with complete or error status.
 *
 * @author brendanx
 */
public class TaskStateChangeJmsSelectorFilter extends JmsSelectorFilter
{
    public String getExpression()
    {
        return PipelineJob.LABKEY_TASKSTATUS_PROPERTY + " = '" + PipelineJob.TaskStatus.complete + "' OR " +
                PipelineJob.LABKEY_TASKSTATUS_PROPERTY + " = '" + PipelineJob.TaskStatus.error + "'";
    }
}
