/*
 * Copyright (c) 2007 LabKey Software Foundation
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
 * <code>TaskStatusJmsSelectorFilter</code> builds and applies a JMS selector for
 * for all jobs with a certain task status.
 *
 * @author brendanx
 */
public class TaskStatusJmsSelectorFilter extends JmsSelectorFilter
{
    private PipelineJob.TaskStatus _status;

    public String getStatus()
    {
        return _status.toString();
    }

    public void setStatus(String status)
    {
        _status = PipelineJob.TaskStatus.valueOf(status);
    }

    public String getExpression()
    {
        return PipelineJob.LABKEY_TASKSTATUS_PROPERTY + " = '" + _status + "'";
    }
}
