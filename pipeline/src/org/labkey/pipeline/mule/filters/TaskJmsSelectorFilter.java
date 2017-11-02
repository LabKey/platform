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

import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.collections.CaseInsensitiveTreeSet;
import org.labkey.api.pipeline.PipelineJob;
import org.mule.providers.jms.filters.JmsSelectorFilter;

import java.util.HashSet;
import java.util.Set;

/**
 * <code>TaskJmsSelectorFilter</code> builds and applies a JMS selector for
 * all registered <code>TaskFactory</code> objects of a specified type.
 *
 * @author brendanx
 */
public class TaskJmsSelectorFilter extends JmsSelectorFilter
{
    private static Set<String> ALL_LOCAL_LOCATIONS = new HashSet<>();

    private static Logger _log = Logger.getLogger(TaskJmsSelectorFilter.class);

    private boolean _includeMonolithic;

    protected Set<String> _locations = new CaseInsensitiveTreeSet();

    public Set<String> getLocations()
    {
        return _locations;
    }

    public void setLocation(@NotNull String location)
    {
        if (location == null)
        {
            throw new IllegalArgumentException("Location may not be null");
        }
        ALL_LOCAL_LOCATIONS.add(location);
        _locations.add(location);
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
        StringBuilder expr = new StringBuilder();
        expr.append("(");
        expr.append("(");
        boolean first = true;
        if (isIncludeMonolithic())
        {
            first = false;
            JobRunJmsSelectorFilter.appendSelector(expr);
        }
        for (String location : getLocations())
        {
            if (first)
                first = false;
            else
                expr.append(" OR ");

            expr.append(PipelineJob.LABKEY_LOCATION_PROPERTY)
                    .append(" = '").append(location).append("'");
        }

        // If nothing has been included yet, make sure this filter never succeeds.
        if (first)
        {
            expr.append("1 = 2");
        }

        expr.append(")");
        expr.append(" AND ");
        expr.append(PipelineJob.LABKEY_TASKSTATUS_PROPERTY);
        expr.append(" = '");
        expr.append(PipelineJob.TaskStatus.waiting.toString());
        expr.append("'");
        expr.append(")");

        _log.debug("JMS Select: " + expr);

        return expr.toString();
    }

    public static Set<String> getAllLocalLocations()
    {
        return ALL_LOCAL_LOCATIONS;
    }
}
