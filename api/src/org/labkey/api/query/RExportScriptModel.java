/*
 * Copyright (c) 2008-2012 LabKey Corporation
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
package org.labkey.api.query;

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.ContainerFilter;

import java.util.List;

/*
* User: Dave
* Date: Aug 13, 2008
* Time: 1:21:48 PM
*/
public class RExportScriptModel extends ExportScriptModel
{
    public RExportScriptModel(QueryView view)
    {
        super(view);
    }

    @Override
    public String getViewName()
    {
        return StringUtils.trimToEmpty(super.getViewName());
    }

    @Override
    public String getSort()
    {
        String sort = super.getSort();

        return null == sort ? "NULL" : sort;
    }

    public String getFilters()
    {
        List<String> filterExprs = getFilterExpressions();

        if (filterExprs.isEmpty())
            return "NULL";

        StringBuilder filtersExpr = new StringBuilder("makeFilter(");
        String sep = "";

        for (String mf : filterExprs)
        {
            filtersExpr.append(sep);
            filtersExpr.append(mf);
            sep = ",";
        }

        filtersExpr.append(")");

        return filtersExpr.toString();
    }

    protected String makeFilterExpression(String name, CompareType operator, String value)
    {
        return "c(\"" + name + "\",\"" + operator.getScriptName() + "\",\"" + value + "\")";
    }

    public String getContainerFilterString()
    {
        ContainerFilter containerFilter = super.getContainerFilter();
        if (null == containerFilter || null == containerFilter.getType())
            return "NULL";
        else
            return (" \"" + (containerFilter.getType().name()) + "\" " );                           
    }
}
