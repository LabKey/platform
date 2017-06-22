/*
 * Copyright (c) 2015-2017 LabKey Corporation
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
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.util.PageFlowUtil;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.stream.Collectors;

/**
 * User: jimp
 * Date: 8/61/15
 * Time: 8:39 PM
 */
public class PythonExportScriptModel extends ExportScriptModel
{
    private int _indentSpaces = 4;

    private static final Logger _log = Logger.getLogger(PythonExportScriptModel.class);

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

    // Our python client api will generate the query filter, so call the constructor
    protected String makeFilterExpression(String name, CompareType operator, String value)
    {
        return "labkey.query.QueryFilter(" + PageFlowUtil.jsString(name) + ", " + PageFlowUtil.jsString(value)
                + ", " + PageFlowUtil.jsString(operator.getPreferredUrlKey()) + ")";
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

    /**
     * Generate the python Server Context object
     * Assumes generating server is export script target.
     * @return
     */
    private String getPythonServerContext()
    {
        StringBuilder sb = new StringBuilder();

        try
        {
            URL baseUrl = new URL(getBaseUrl());
            sb.append("server_context = labkey.utils.create_server_context(");
            sb.append(PageFlowUtil.jsString(baseUrl.getAuthority())).append(", ");
            sb.append(PageFlowUtil.jsString(getFolderPath().substring(1))).append(", ");

            // 25082: The server may not have a context path
            if (baseUrl.getPath() != null && baseUrl.getPath().length() > 1)
                sb.append(PageFlowUtil.jsString(baseUrl.getPath().substring(1))).append(", ");

            sb.append("use_ssl=").append(baseUrl.getProtocol().equals("https") ? "True" : "False");
            sb.append(")\n");
        }
        catch (MalformedURLException e)
        {
            // Log error
            _log.error("Unable to retrieve server url to generate python client request context", e);

            // Add comment and example to export script
            return "# Unable to generate labkey request context. Example usage:\n" +
                "# server_context = labkey.utils.create_server_context(domain, container_path, context_path=None, use_ssl=True)\n";
        }

        return sb.toString();
    }

    private String getStandardScriptParameters()
    {
        StringBuilder params = new StringBuilder();
        String indent = StringUtils.repeat(" ", _indentSpaces);

        params.append(indent).append("server_context=server_context").append(",\n");
        params.append(indent).append("schema_name=").append(PageFlowUtil.jsString(getSchemaName())).append(",\n");
        params.append(indent).append("query_name=").append(PageFlowUtil.jsString(getQueryName()));
        if (null != getViewName())
            params.append(",\n").append(indent).append("view_name=").append(PageFlowUtil.jsString(getViewName()));

        // Generate filter string
        String filters = getFilters();
        if (null != filters)
            params.append(",\n").append(indent).append("filter_array=").append(filters);

        // Generate Sort string
        if (hasSort())
            params.append(",\n").append(indent).append("sort=").append(PageFlowUtil.jsString(getSort()));

        // Generate ContainerFilter
        if (hasContainerFilter())
            params.append(",\n").append(indent).append("container_filter=").append(PageFlowUtil.jsString(getContainerFilterTypeName()));

        if (hasQueryParameters())
        {
            params.append(",\n").append(indent).append("parameters={");
            params.append(getQueryParameters().entrySet().stream()
                .map(e -> "'" + e.getKey() + "': '" + e.getValue() + "'")
                .collect(Collectors.joining(", ")));
            params.append("}");
        }

        return params.toString();
    }

    @Override
    public String getScriptExportText()
    {
        StringBuilder sb = new StringBuilder();

        sb.append("# This script targets the client api version 0.4.0 and later").append("\n");
        sb.append("import labkey").append("\n\n");

        //Generate server context object
        sb.append(getPythonServerContext()).append("\n");
        sb.append("my_results = labkey.query.select_rows(\n");

        //Generate query parameters
        sb.append(getStandardScriptParameters()).append("\n");
        sb.append(")\n");
        return sb.toString();
    }
}
