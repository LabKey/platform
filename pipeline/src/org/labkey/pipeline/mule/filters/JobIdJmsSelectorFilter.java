/*
 * Copyright (c) 2012 LabKey Corporation
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
 * <code>JobRunJmsSelectorFilter</code> builds and applies a JMS selector for a single job based on its GUID
 *
 * @author jeckels
 */
public class JobIdJmsSelectorFilter extends JmsSelectorFilter
{
    public static StringBuffer appendSelector(StringBuffer expr, String jobId)
    {
        return expr.append(PipelineJob.LABKEY_JOBID_PROPERTY)
                .append(" = '").append(jobId).append("'");
    }

    public JobIdJmsSelectorFilter(String jobId)
    {
        setExpression(appendSelector(new StringBuffer(), jobId).toString());
    }
}