/*
 * Copyright (c) 2007-2008 LabKey Corporation
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
import org.labkey.api.pipeline.TaskFactory;
import org.labkey.pipeline.api.PipelineJobServiceImpl;
import org.mule.providers.jms.filters.JmsSelectorFilter;

/**
 * <code>TaskJmsSelectorFilter</code> builds and applies a JMS selector for
 * all registered <code>TaskFactory</code> objects of a specified type.
 *
 * @author brendanx
 */
public class TaskJmsSelectorFilter extends JmsSelectorFilter
{
    private TaskFactory.ExecutionLocation _location;
    private boolean _includeMonolithic;

    public String getLocation()
    {
        return _location.toString();
    }

    public void setLocation(String location)
    {
        _location = TaskFactory.ExecutionLocation.valueOf(location);
    }

    public boolean isIncludeMonolithic()
    {
        return _includeMonolithic;
    }

    public void setIncludeMonolithic(boolean includeMonolithic)
    {
        _includeMonolithic = includeMonolithic;
    }

    public String getExpression()
    {
        StringBuffer expr = new StringBuffer();
        expr.append("(");
        expr.append("(");
        boolean first = true;
        if (isIncludeMonolithic())
        {
            first = false;
            JobRunJmsSelectorFilter.appendSelector(expr);
        }
        for (TaskFactory factory : PipelineJobServiceImpl.get().getTaskFactories())
        {
            if (!_location.equals(factory.getExecutionLocation()))
                continue;
            
            if (first)
                first = false;
            else
                expr.append(" OR ");

            expr.append(PipelineJob.LABKEY_TASKID_PROPERTY)
                    .append(" = '").append(factory.getId()).append("'");
        }

        // If nothing has been included yet, make sure this filter never succeeds.
        if (first)
        {
            expr.append(PipelineJob.LABKEY_TASKID_PROPERTY)
                    .append(" = '").append(PipelineJob.Task.class.getName()).append("'");
        }
        expr.append(")");
        expr.append(" AND ");
        expr.append(PipelineJob.LABKEY_TASKSTATUS_PROPERTY).append(" = 'waiting'");
        expr.append(")");

        return expr.toString();
    }
}
