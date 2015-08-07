/*
 * Copyright (c) 2012-2015 LabKey Corporation
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
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.util.PageFlowUtil;

import java.util.List;

/**
 * User: jimp
 * Date: 8/61/15
 * Time: 8:39 PM
 */
public class PythonExportScriptModel extends ExportScriptModel
{
    private int _indentSpaces = 0;

    public PythonExportScriptModel(QueryView view)
    {
        super(view);
    }

    @Nullable
    public String getFilters()
    {
        String indent = StringUtils.repeat(" ", _indentSpaces);

        List<String> filterExprs = getFilterExpressions();
        if (null == filterExprs || filterExprs.size() == 0)
            return null;

        StringBuilder ret = new StringBuilder("[");
        String sep = "\n";

        for (String filterExpr : filterExprs)
        {
            ret.append(sep).append(indent).append(indent);
            ret.append(filterExpr);
            sep = ",\n";
        }
        ret.append("\n").append(indent).append("]");
        return ret.toString();
    }

    // Our python client api expects filters with operators in the middle
    protected String makeFilterExpression(String name, CompareType operator, String value)
    {
        return "[" + PageFlowUtil.jsString(name) + ", "
                + operator.getPreferredUrlKey() + ", '" + PageFlowUtil.jsString(value) + "']";
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

    // Produce Python code block containing all the standard query parameters.
    public String getStandardScriptParameters(int indentSpaces)
    {
        String indent = StringUtils.repeat(" ", indentSpaces);
        _indentSpaces = indentSpaces;
        StringBuilder params = new StringBuilder();

        params.append(indent).append("baseUrl = ").append(PageFlowUtil.jsString(getBaseUrl())).append(",\n");
        params.append(indent).append("containerPath = ").append(PageFlowUtil.jsString(getFolderPath())).append(",\n");
        params.append(indent).append("schemaName = ").append(PageFlowUtil.jsString(getSchemaName())).append(",\n");
        if (null != getViewName())
            params.append(indent).append("viewName = ").append(PageFlowUtil.jsString(getViewName())).append(",\n");
        params.append(indent).append("queryName = ").append(PageFlowUtil.jsString(getQueryName())).append(",\n");
        params.append(indent).append("columns = ").append(PageFlowUtil.jsString(getColumns()));  // Inconsistent with R and SAS, which don't include view columns

        String filters = getFilters();

        if (null != filters)
            params.append(",\n").append(indent).append("filterArray = ").append(filters);
        ContainerFilter containerFilter = getContainerFilter();
        if (null != containerFilter)
        {
            ContainerFilter.Type type = containerFilter.getType();
            if (null != type)
                params.append(",\n").append(indent).append("containerFilterName = '").append(type.name()).append("'");
        }

        String sort = getSort();

        if (sort != null)
            params.append(",\n").append(indent).append("sort = ").append(PageFlowUtil.jsString(sort));
        return params.toString();
    }

    @Override
    public String getScriptExportText()
    {
        String indent = StringUtils.repeat(" ", 4);

        StringBuilder sb = new StringBuilder();

        sb.append("import labkey").append("\n\n");

        sb.append("myresults = labkey.query.selectRows(\n");
        sb.append(getStandardScriptParameters(4)).append("\n");
        sb.append(")\n");

        sb.append("\n");
        sb.append("for row in myresults['rows'] :\n");
        sb.append(indent).append("print row").append("\n");
        return sb.toString();
    }
}
