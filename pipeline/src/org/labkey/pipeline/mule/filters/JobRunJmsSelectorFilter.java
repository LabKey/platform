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
 * <code>JobRunJmsSelectorFilter</code> builds and applies a JMS selector for
 * pipeline jobs with no task pipeline.
 *
 * @author brendanx
 */
public class JobRunJmsSelectorFilter extends JmsSelectorFilter
{
    public static StringBuilder appendSelector(StringBuilder expr)
    {
        return expr.append(PipelineJob.LABKEY_TASKPIPELINE_PROPERTY)
                .append(" = '").append(PipelineJob.class.getName()).append("'");
    }

    public JobRunJmsSelectorFilter()
    {
        setExpression(appendSelector(new StringBuilder()).toString());
    }
}