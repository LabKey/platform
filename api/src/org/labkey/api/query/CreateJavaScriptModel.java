/*
 * Copyright (c) 2009-2012 LabKey Corporation
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
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.util.PageFlowUtil;

import java.util.List;

/*
* User: Dave
* Date: Apr 2, 2009
* Time: 12:44:51 PM
*/
public class CreateJavaScriptModel extends ExportScriptModel
{
    public CreateJavaScriptModel(QueryView view)
    {
        super(view);
    }

    public String getFilters()
    {
        List<String> filterExprs = getFilterExpressions();
        if (null == filterExprs || filterExprs.size() == 0)
            return "null";
        
        StringBuilder ret = new StringBuilder("[");
        String sep = "";
        for (String filterExpr : filterExprs)
        {
            ret.append(sep);
            ret.append(filterExpr);
            sep = ",";
        }
        ret.append("]");
        return ret.toString();
    }

    protected String makeFilterExpression(String name, CompareType operator, String value)
    {
        return "LABKEY.Filter.create(" +PageFlowUtil.jsString(name) + ", "
                + PageFlowUtil.jsString(value) + ", LABKEY.Filter.Types." + operator.getScriptName() + ")";
    }

    public String getColumns()
    {
        StringBuilder ret = new StringBuilder();
        String sep = "";
        for (DisplayColumn dc : getQueryView().getDisplayColumns())
        {
            if (dc.isQueryColumn())
            {
                ret.append(sep);
                ret.append(dc.getColumnInfo().getName());
                sep = ",";
            }
        }
        return ret.toString();
    }

    @Override
    public String getSort()
    {
        //remove enclosing double-quotes since we'll use PageFlowUtil.jsString() on the result
        String sort = super.getSort();
        return null != sort ? PageFlowUtil.jsString(sort.substring(1, sort.length() - 1)) : "null";
    }

    // Produce javascript code block containing all the standard query parameters.  Callers need to wrap this block in
    // curly braces (at a minimum) and modify/add parameters as appropriate.
    public String getStandardJavaScriptParameters(int indentSpaces, boolean includeStandardCallbacks)
    {
        String indent = StringUtils.repeat(" ", indentSpaces);
        StringBuilder params = new StringBuilder();
        params.append(indent).append("requiredVersion: 9.1,\n");
        params.append(indent).append("schemaName: ").append(PageFlowUtil.jsString(getSchemaName())).append(",\n");

        if (null != getViewName())
            params.append(indent).append("viewName: ").append(PageFlowUtil.jsString(getViewName())).append(",\n");

        params.append(indent).append("queryName: ").append(PageFlowUtil.jsString(getQueryName())).append(",\n");
        params.append(indent).append("columns: ").append(PageFlowUtil.jsString(getColumns())).append(",\n");  // TODO: Inconsistent with R and SAS, which don't include view columns
        params.append(indent).append("filterArray: ").append(getFilters()).append(",\n");

        ContainerFilter containerFilter = getContainerFilter();

        if (null != containerFilter && null != containerFilter.getType())
            params.append(indent).append("containerFilter: '").append(containerFilter.getType().name()).append("',\n");

        params.append(indent).append("sort: ").append(getSort());

        if (includeStandardCallbacks)
        {
            params.append(",\n");
            params.append(indent).append("success: onSuccess,\n");
            params.append(indent).append("error: onError");
        }

        return params.toString();
    }
}
